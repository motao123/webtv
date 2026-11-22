#!/usr/bin/env bash
#
# Keep every version literal in step before a release.
#
# The release preflight refuses to build when README or the Pages site still
# advertise an older version, and when the Pages download buttons name bare
# <flavor>.apk files. Those bare names stop existing the moment release assets
# ship as <flavor>-<version>.apk — which is exactly what keeps a new release off
# the CDN's cache: a fresh name is a URL no edge has ever stored, so it always
# misses instead of serving the previous build for 300s. Rather than hand-edit a
# dozen places, run this.
#
#   bash scripts/bump_version.sh 5.16.0            # apply
#   bash scripts/bump_version.sh 5.16.0 --dry-run  # print the lines that would change
#
# Still manual afterwards (this script deliberately will not guess):
#   1. app/build.gradle: versionCode + 1
#   2. CHANGELOG.md: add a matching "## <version>" section — the release job
#      refuses to publish without one.
set -euo pipefail

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  echo "usage: bash scripts/bump_version.sh <x.y.z> [--dry-run]" >&2
  exit 2
fi
case "$VERSION" in
  *[!0-9.]*) echo "版本号应为 x.y.z 形式（收到 '$VERSION'）" >&2; exit 2 ;;
  .* | *.) echo "版本号应为 x.y.z 形式（收到 '$VERSION'）" >&2; exit 2 ;;
esac
case "$VERSION" in
  *.*.*) ;;
  *) echo "版本号应为 x.y.z 形式（收到 '$VERSION'）" >&2; exit 2 ;;
esac

DRY=0
[ "${2:-}" = "--dry-run" ] && DRY=1

cd "$(dirname "$0")/.."
for f in README.md docs/index.html app/build.gradle; do
  [ -f "$f" ] || { echo "找不到 $f —— 请在仓库根目录运行" >&2; exit 2; }
done

FLAVORS="mobile-arm64_v8a mobile-universal mobile-lite-arm64_v8a mobile-lite-universal leanback-arm64_v8a leanback-universal leanback-lite-arm64_v8a leanback-lite-universal mobile-armeabi_v7a leanback-armeabi_v7a"

apply() { # <file> <sed expression without flags>
  local file="$1" expr="$2"
  if [ "$DRY" = 1 ]; then
    sed -nE "${expr}p" "$file" | sed 's/^/    /'
  else
    sed -i -E "$expr" "$file"
  fi
}

echo "== README.md 版本标记 =="
apply README.md "s/^(最新版本[：:]?[[:space:]]*\*\*v)[0-9]+\.[0-9]+\.[0-9]+/\1${VERSION}/"

echo "== docs/index.html 版本标记 =="
apply docs/index.html "s/(<span class=\"sub\">v)[0-9]+\.[0-9]+\.[0-9]+/\1${VERSION}/"
apply docs/index.html "s/(最新版本 <strong[^>]*>v)[0-9]+\.[0-9]+\.[0-9]+/\1${VERSION}/"

echo "== docs/index.html 下载链接与显示名 =="
for f in $FLAVORS; do
  apply docs/index.html "s|/download/${f}(-[0-9][0-9.]*)?\.apk|/download/${f}-${VERSION}.apk|g"
  apply docs/index.html "s|/file/apk/${f}(-[0-9][0-9.]*)?\.apk|/file/apk/${f}-${VERSION}.apk|g"
  apply docs/index.html "s|(<div class=\"dl-name\">)${f}(-[0-9][0-9.]*)?\.apk(</div>)|\1${f}-${VERSION}.apk\3|g"
done

echo "== app/build.gradle versionName =="
apply app/build.gradle "s/^([[:space:]]*versionName \")[0-9]+\.[0-9]+\.[0-9]+(\")/\1${VERSION}\2/"

echo
if [ "$DRY" = 1 ]; then
  echo "dry-run：没有写入任何文件。"
else
  echo "已写入。还需要手动完成："
fi
echo "  1) app/build.gradle 的 versionCode +1"
echo "  2) CHANGELOG.md 增加 '## ${VERSION}' 小节"
echo "  3) commit + push，然后打 tag v${VERSION}"
