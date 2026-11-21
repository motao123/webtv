package com.fongmi.android.tv.server.process;

import java.io.InputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-128 segment decryption for the TS cast path, in plain Java so it runs both on Android and
 * on the JVM test harness. HLS encrypts each segment independently with AES-CBC, so a decrypted
 * segment stream can be appended just like a plain one.
 */
final class HlsAes {

    private HlsAes() {
    }

    /** Builds the 16-byte IV from the hex value of an #EXT-X-KEY IV attribute. */
    static byte[] hexIv(String hex) {
        // Left-pad with zeros so short literals like 0x1 still denote the required 128 bits.
        String digits = hex.length() > 32 ? hex.substring(hex.length() - 32) : hex;
        while (digits.length() < 32) digits = "0" + digits;
        byte[] iv = new byte[16];
        for (int i = 0; i < 16; i++) iv[i] = (byte) Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16);
        return iv;
    }

    /** Builds the 16-byte IV from a media sequence number, the HLS default when no IV is given. */
    static byte[] sequenceIv(long sequence) {
        byte[] iv = new byte[16];
        for (int i = 15; i >= 0; i--) {
            iv[i] = (byte) sequence;
            sequence >>>= 8;
        }
        return iv;
    }

    /**
     * Wraps a segment stream in the matching CBC decryption. PKCS5 is the JCE name for the PKCS7
     * padding HLS uses on 16-byte blocks.
     */
    static InputStream decrypt(InputStream input, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return new CipherInputStream(input, cipher);
    }
}
