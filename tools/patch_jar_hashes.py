#!/usr/bin/env python3
"""
patch_jar_hashes.py — 给 TVBox/CSP 配置源补 ;sha256; 哈希

5.5.3 之后，远程 jar 字段必须带 ;sha256; 或 ;md5; 完整性校验，
否则会被静默拒绝加载。本工具下载配置、解析所有 jar、计算
SHA-256，输出一份带哈希的修补版。

用法：

  python tools/patch_jar_hashes.py <config_url> [-o patched.json]
                                    [--algo sha256|md5]
                                    [--dry-run]

示例：

  python tools/patch_jar_hashes.py \
      https://example.com/path/to/api.json

  # 只看哪些 jar 会被处理，不下载 / 不写文件：
  python tools/patch_jar_hashes.py <url> --dry-run

实现说明：

  - 相对路径（./spider.jar、../lib/x.jar）按 RFC 3986 相对 config URL 解析
  - assets://、file:// 本地 jar 不处理（不下载不补 hash）
  - 已带 ;sha256;/;md5; 的 jar 跳过（信任作者给的）
  - 哈希算法默认 SHA-256（5.5.3 推荐），可切 MD5
"""

import argparse
import hashlib
import json
import sys
from pathlib import PurePosixPath
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

USER_AGENT = "webtv-patch-jar-hashes/1.0"


def fetch(url, timeout=30):
    """GET url，返回 bytes。失败抛异常。"""
    req = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(req, timeout=timeout) as resp:
        return resp.read()


def resolve_jar(base_url, jar):
    """把 jar 字段相对于 config URL 解析成绝对 URL。

    已经是 http(s)/assets/file 的原样返回；
    相对路径按 RFC 3986 解析（./spider.jar -> 同目录）。
    """
    if not jar:
        return ""
    s = jar.strip()
    if not s or s.startswith(("http://", "https://", "assets://", "file://")):
        return s
    # ;sha256;... / ;md5;... 这种后缀先剥掉再解析
    for sep in (";sha256;", ";md5;"):
        if sep in s:
            s = s.split(sep, 1)[0]
            break
    return urljoin(base_url, s)


def collect_jars(config, base_url):
    """从配置里收集所有 jar URL（去重，保序）。

    返回 [(jar_url_without_hash, original_field_value), ...]
    """
    seen = set()
    out = []

    def add(jar):
        if not jar or not isinstance(jar, str):
            return
        # 已经带 hash 的跳过
        for sep in (";sha256;", ";md5;"):
            if sep in jar:
                return
        resolved = resolve_jar(base_url, jar)
        if not resolved or resolved in seen:
            return
        seen.add(resolved)
        out.append((resolved, jar))

    # 根 spider
    root = config.get("spider")
    if isinstance(root, str):
        add(root)

    # sites[].jar
    for site in config.get("sites", []) or []:
        if isinstance(site, dict):
            add(site.get("jar"))

    # lives[].jar
    for live in config.get("lives", []) or []:
        if isinstance(live, dict):
            add(live.get("jar"))

    # parses 偶尔也会带 jar，保守起见扫一下
    for parse in config.get("parses", []) or []:
        if isinstance(parse, dict):
            add(parse.get("jar"))

    return out


def hash_bytes(data, algo):
    h = hashlib.new(algo)
    h.update(data)
    return h.hexdigest()


def patch_field(original_jar, digest, algo):
    """把 digest 拼到 jar 字段后面。

    ./spider.jar           -> ./spider.jar;sha256;abc...
    https://.../spider.jar -> https://.../spider.jar;sha256;abc...
    """
    sep = f";{algo};"
    # 防御性剥掉已有 hash 再拼
    for old in (";sha256;", ";md5;"):
        if old in original_jar:
            original_jar = original_jar.split(old, 1)[0]
    return f"{original_jar}{sep}{digest}"


def patch_config(config, base_url, algo, dry_run, downloader, hasher):
    """下载所有 jar、算 hash、就地改 config。返回 jar 报告。"""
    jars = collect_jars(config, base_url)
    report = []
    for abs_url, original in jars:
        try:
            if dry_run:
                report.append((original, abs_url, "skipped(dry-run)", ""))
                continue
            data = downloader(abs_url)
            digest = hasher(data, algo)
            patched = patch_field(original, digest, algo)
            # 替换 config 里所有相同的 jar 字符串
            replace_in_config(config, original, patched)
            report.append((original, abs_url, "ok", digest))
        except (URLError, HTTPError, TimeoutError) as e:
            report.append((original, abs_url, f"failed: {e}", ""))
    return report


def replace_in_config(obj, old, new):
    """递归把 config 里所有等于 old 的字符串替换成 new。"""
    if isinstance(obj, dict):
        for k, v in list(obj.items()):
            if isinstance(v, str) and v == old:
                obj[k] = new
            else:
                replace_in_config(v, old, new)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            if isinstance(v, str) and v == old:
                obj[i] = new
            else:
                replace_in_config(v, old, new)


def main():
    p = argparse.ArgumentParser(description="为 TVBox/CSP 配置源补 jar 完整性哈希")
    p.add_argument("url", help="配置 JSON 的 URL（http/https）")
    p.add_argument("-o", "--out", help="输出文件路径（默认 stdout）")
    p.add_argument("--algo", choices=("sha256", "md5"), default="sha256",
                   help="哈希算法（默认 sha256）")
    p.add_argument("--dry-run", action="store_true",
                   help="只列出 jar，不下载、不写文件")
    p.add_argument("--timeout", type=int, default=30, help="HTTP 超时（秒）")
    args = p.parse_args()

    try:
        raw = fetch(args.url, timeout=args.timeout)
    except (URLError, HTTPError, TimeoutError) as e:
        print(f"failed to download config: {e}", file=sys.stderr)
        sys.exit(1)

    try:
        config = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        print(f"config is not valid UTF-8 JSON: {e}", file=sys.stderr)
        sys.exit(1)

    def downloader(url):
        return fetch(url, timeout=args.timeout)

    report = patch_config(
        config,
        base_url=args.url,
        algo=args.algo,
        dry_run=args.dry_run,
        downloader=downloader,
        hasher=hash_bytes,
    )

    # 报告
    print(f"# jars: {len(report)}", file=sys.stderr)
    for original, abs_url, status, digest in report:
        if digest:
            line = f"  {original:60s} -> {abs_url}  [{status}] {args.algo}={digest}"
        else:
            line = f"  {original:60s} -> {abs_url}  [{status}]"
        print(line, file=sys.stderr)

    if args.dry_run:
        return

    out = json.dumps(config, ensure_ascii=False, indent=4)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(out)
            f.write("\n")
        print(f"# written: {args.out}", file=sys.stderr)
    else:
        sys.stdout.write(out + "\n")


if __name__ == "__main__":
    main()
