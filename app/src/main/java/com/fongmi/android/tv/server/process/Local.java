package com.fongmi.android.tv.server.process;

import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;
import static fi.iki.elonen.NanoHTTPD.getMimeTypeForFile;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.utils.Path;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Local implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/file") || url.startsWith("/upload") || url.startsWith("/newFolder") || url.startsWith("/delFolder") || url.startsWith("/delFile");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (url.startsWith("/file")) return getFile(session.getHeaders(), session.getParms(), url);
        if (url.startsWith("/upload")) return upload(session.getParms(), files);
        if (url.startsWith("/newFolder")) return newFolder(session.getParms());
        if (url.startsWith("/delFolder") || url.startsWith("/delFile")) return delete(session.getParms());
        return null;
    }

    private Response getFile(Map<String, String> headers, Map<String, String> params, String path) {
        try {
            File file = safePath(URLDecoder.decode(path.substring(5), StandardCharsets.UTF_8));
            if (file.isDirectory()) return getFolder(file);
            if (file.isFile()) return getFile(headers, params, file, getMimeTypeForFile(path));
            throw new FileNotFoundException();
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private Response getFile(Map<String, String> headers, Map<String, String> params, File file, String mime) throws IOException {
        Response response = getFile(headers, file, mime);
        if (isDownload(params)) addDownloadHeader(response, file.getName());
        return response;
    }

    private Response upload(Map<String, String> params, Map<String, String> files) {
        try {
            File dir = safePath(params.get("path"));
            if (!dir.isDirectory() && !dir.mkdirs()) return Nano.error(Status.BAD_REQUEST, "Invalid upload path");
            for (String k : files.keySet()) {
                String fn = safeName(params.get(k));
                File temp = new File(files.get(k));
                if (fn.toLowerCase().endsWith(".zip")) unzip(temp, dir);
                else Path.copy(temp, safeChild(dir, fn));
            }
            return Nano.ok();
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private Response newFolder(Map<String, String> params) {
        try {
            safeChild(safePath(params.get("path")), safeName(params.get("name"))).mkdirs();
            return Nano.ok();
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private Response delete(Map<String, String> params) {
        try {
            File file = safePath(params.get("path"));
            if (file.getCanonicalFile().equals(Path.root().getCanonicalFile())) return Nano.error(Status.BAD_REQUEST, "Refuse to delete root");
            Path.clear(file);
            return Nano.ok();
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private Response getFolder(File dir) {
        File rootDir = Path.root();
        String rootPath = rootDir.getAbsolutePath();
        JsonArray files = new JsonArray();
        for (File file : Path.list(dir)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", file.getName());
            obj.addProperty("path", relativeTo(file, rootPath));
            obj.addProperty("time", Formatters.LOCAL_DATETIME.format(Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault())));
            obj.addProperty("dir", file.isDirectory() ? 1 : 0);
            obj.addProperty("size", file.isDirectory() ? 0 : file.length());
            files.add(obj);
        }
        JsonObject info = new JsonObject();
        info.addProperty("parent", parentOf(dir, rootDir, rootPath));
        info.add("files", files);
        return Nano.ok(info.toString());
    }

    private Response getFile(Map<String, String> headers, File file, String mime) throws IOException {
        long fileLen = file.length();
        String etag = etag(file, fileLen);
        String ifNoneMatch = headers.get("if-none-match");
        if (ifNoneMatch != null && (ifNoneMatch.equals("*") || ifNoneMatch.equals(etag))) {
            return newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
        }
        HttpRange range = HttpRange.from(fileLen, headers, etag);
        if (!range.valid()) return createRangeNotSatisfiableResponse(fileLen);
        FileInputStream fis = new FileInputStream(file);
        skip(fis, range.start);
        Response res;
        if (range.isPartial(fileLen)) {
            res = newFixedLengthResponse(Status.PARTIAL_CONTENT, mime, fis, range.length);
            res.addHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + fileLen);
        } else {
            res = newFixedLengthResponse(Status.OK, mime, fis, range.length);
        }
        res.addHeader("Content-Length", String.valueOf(range.length));
        res.addHeader("Accept-Ranges", "bytes");
        res.addHeader("ETag", etag);
        return res;
    }

    private boolean isDownload(Map<String, String> params) {
        String value = params.get("download");
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private void addDownloadHeader(Response response, String name) {
        String fallback = name.replaceAll("[\\\\\"\\r\\n]", "_");
        String encoded;
        try {
            encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            encoded = fallback;
        }
        response.addHeader("Content-Disposition", "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded);
        response.addHeader("X-Content-Type-Options", "nosniff");
    }

    private String etag(File file, long fileLen) {
        CRC32 crc = new CRC32();
        crc.update((file.getAbsolutePath() + file.lastModified() + fileLen).getBytes());
        return Long.toHexString(crc.getValue());
    }

    private Response createRangeNotSatisfiableResponse(long fileLen) {
        Response res = newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
        res.addHeader("Content-Range", "bytes */" + fileLen);
        return res;
    }

    private File safePath(String path) throws IOException {
        if (path == null) throw new IOException("Invalid path");
        String clean = path.replace("file:/", "");
        while (clean.startsWith("/")) clean = clean.substring(1);
        File file = new File(Path.root(), clean);
        return safeFile(file);
    }

    private File safeChild(File dir, String name) throws IOException {
        return safeFile(new File(dir, name));
    }

    private File safeFile(File file) throws IOException {
        File root = Path.root().getCanonicalFile();
        File canonical = file.getCanonicalFile();
        String rootPath = root.getPath();
        String filePath = canonical.getPath();
        if (!canonical.equals(root) && !filePath.startsWith(rootPath + File.separator)) throw new IOException("Path outside root");
        return canonical;
    }

    private String safeName(String name) throws IOException {
        if (name == null || name.isEmpty()) throw new IOException("Invalid file name");
        if (name.contains("/") || name.contains("\\") || name.contains("..")) throw new IOException("Invalid file name");
        for (int i = 0; i < name.length(); i++) if (Character.isISOControl(name.charAt(i))) throw new IOException("Invalid file name");
        return name;
    }

    private void unzip(File zip, File dir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zip)) {
            for (ZipEntry entry : java.util.Collections.list(zipFile.entries())) {
                if (entry.isDirectory()) {
                    safeFile(new File(dir, entry.getName())).mkdirs();
                    continue;
                }
                File out = safeFile(new File(dir, entry.getName()));
                Path.copy(zipFile.getInputStream(entry), out);
            }
        }
    }

    private void skip(InputStream is, long bytesToSkip) throws IOException {
        if (bytesToSkip <= 0) return;
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = is.skip(remaining);
            if (skipped <= 0) throw new IOException("Failed to skip desired number of bytes");
            remaining -= skipped;
        }
    }

    private static String relativeTo(File file, String rootPath) {
        String path = file.getAbsolutePath();
        return path.startsWith(rootPath) ? path.substring(rootPath.length()) : path;
    }

    private static String parentOf(File dir, File rootDir, String rootPath) {
        if (dir.equals(rootDir)) return ".";
        File parent = dir.getParentFile();
        if (parent == null || parent.equals(rootDir)) return "";
        return relativeTo(parent, rootPath);
    }

    private record HttpRange(long start, long end, long length, boolean valid) {

        public boolean isPartial(long total) {
            return length < total;
        }

        public static HttpRange invalid() {
            return new HttpRange(0, 0, 0, false);
        }

        public static HttpRange from(long fileLen, Map<String, String> headers, String etag) {
            long start = 0;
            long end = fileLen - 1;
            String rangeHeader = headers.get("range");
            String ifRange = headers.get("if-range");
            if (ifRange != null && !ifRange.equals(etag)) rangeHeader = null;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                try {
                    String[] parts = rangeHeader.substring(6).split("-", 2);
                    if (!parts[0].isEmpty()) start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
                    if (start >= fileLen || start > end) return invalid();
                } catch (NumberFormatException e) {
                    return invalid();
                }
            }
            if (end >= fileLen) end = fileLen - 1;
            return new HttpRange(start, end, end - start + 1, true);
        }
    }
}
