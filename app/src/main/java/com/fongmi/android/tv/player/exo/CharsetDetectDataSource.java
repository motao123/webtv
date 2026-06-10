package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@UnstableApi
public class CharsetDetectDataSource implements DataSource {

    private static final int MAX_SUBTITLE_BYTES = 5 * 1024 * 1024;
    private static final int DETECT_SIZE = 4096;

    private final DataSource upstream;
    private final ByteArrayOutputStream buffer;
    private ByteArrayInputStream convertedStream;
    private long totalBuffered;
    private boolean passthrough;
    private boolean opened;

    public CharsetDetectDataSource(DataSource upstream) {
        this.upstream = upstream;
        this.buffer = new ByteArrayOutputStream();
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        long size = upstream.open(dataSpec);
        buffer.reset();
        totalBuffered = 0;
        passthrough = false;
        convertedStream = null;
        opened = true;
        return size;
    }

    @Override
    public int read(@NonNull byte[] target, int offset, int length) throws IOException {
        if (!opened) throw new IOException("DataSource not opened");
        if (convertedStream != null) return convertedStream.read(target, offset, length);
        if (passthrough) {
            if (buffer.size() > 0) return drainBuffer(target, offset, length);
            return upstream.read(target, offset, length);
        }
        byte[] tmp = new byte[Math.min(length, 8192)];
        int read = upstream.read(tmp, 0, tmp.length);
        if (read <= 0) {
            byte[] result = transcode(buffer.toByteArray());
            convertedStream = new ByteArrayInputStream(result);
            return convertedStream.read(target, offset, length);
        }
        totalBuffered += read;
        if (totalBuffered > MAX_SUBTITLE_BYTES) {
            passthrough = true;
            buffer.write(tmp, 0, read);
            return drainBuffer(target, offset, length);
        }
        buffer.write(tmp, 0, read);
        if (totalBuffered >= DETECT_SIZE && !isTextContent(buffer.toByteArray())) {
            passthrough = true;
            return drainBuffer(target, offset, length);
        }
        return read;
    }

    private int drainBuffer(@NonNull byte[] target, int offset, int length) {
        byte[] data = buffer.toByteArray();
        int len = Math.min(data.length, length);
        System.arraycopy(data, 0, target, offset, len);
        byte[] remaining = new byte[data.length - len];
        System.arraycopy(data, len, remaining, 0, remaining.length);
        buffer.reset();
        if (remaining.length > 0) {
            buffer.write(remaining, 0, remaining.length);
        }
        return len == 0 && length > 0 ? 0 : len;
    }

    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public Map<String, java.util.List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        upstream.close();
        buffer.reset();
        convertedStream = null;
    }

    private static boolean isTextContent(byte[] data) {
        int len = Math.min(data.length, DETECT_SIZE);
        int nonText = 0;
        for (int i = 0; i < len; i++) {
            int b = data[i] & 0xFF;
            if (b == 0x00) return false;
            if (b < 0x09 || (b > 0x0D && b < 0x20)) nonText++;
        }
        return nonText < len * 0.05;
    }

    private static byte[] transcode(byte[] bytes) {
        if (bytes.length == 0) return bytes;
        if (appearsUtf8(bytes)) return bytes;
        for (String name : new String[]{"GB18030", "GBK"}) {
            try {
                CharsetDecoder decoder = Charset.forName(name).newDecoder();
                decoder.onMalformedInput(CodingErrorAction.REPORT);
                decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                CharBuffer result = decoder.decode(ByteBuffer.wrap(bytes));
                String text = result.toString();
                if (hasCJK(text)) return text.getBytes(StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return bytes;
    }

    private static boolean appearsUtf8(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) return true;
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            int len;
            if (b <= 0x7F) len = 1;
            else if (b >= 0xC2 && b <= 0xDF) len = 2;
            else if (b >= 0xE0 && b <= 0xEF) len = 3;
            else if (b >= 0xF0 && b <= 0xF4) len = 4;
            else return false;
            for (int j = 1; j < len; j++) {
                if (i + j >= bytes.length) return false;
                int nb = bytes[i + j] & 0xFF;
                if ((nb & 0xC0) != 0x80) return false;
            }
            i += len;
        }
        return true;
    }

    private static boolean hasCJK(String text) {
        for (int i = 0; i < Math.min(text.length(), 500); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    public static final class Factory implements DataSource.Factory {

        private final DataSource.Factory upstreamFactory;

        public Factory(DataSource.Factory upstreamFactory) {
            this.upstreamFactory = upstreamFactory;
        }

        @NonNull
        @Override
        public DataSource createDataSource() {
            return new CharsetDetectDataSource(upstreamFactory.createDataSource());
        }
    }
}
