package com.fongmi.android.tv.service;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class DriveMobileCrypto {

    private static final byte[] KEY = "PVGDwmcvfs1uV3d1".getBytes(StandardCharsets.UTF_8);
    private static final int BLOCK_SIZE = 16;

    static String encrypt(String text) throws Exception {
        byte[] iv = new byte[BLOCK_SIZE];
        new SecureRandom().nextBytes(iv);
        byte[] plain = pad(text.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plain);
        byte[] output = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, output, 0, iv.length);
        System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
        return Base64.encodeToString(output, Base64.NO_WRAP);
    }

    static String decrypt(String text) throws Exception {
        byte[] encrypted = Base64.decode(text, Base64.DEFAULT);
        if (encrypted.length < BLOCK_SIZE) throw new IllegalArgumentException("响应长度异常");
        byte[] iv = new byte[BLOCK_SIZE];
        byte[] body = new byte[encrypted.length - BLOCK_SIZE];
        System.arraycopy(encrypted, 0, iv, 0, BLOCK_SIZE);
        System.arraycopy(encrypted, BLOCK_SIZE, body, 0, body.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(iv));
        return new String(unpad(cipher.doFinal(body)), StandardCharsets.UTF_8);
    }

    private static byte[] pad(byte[] input) {
        int padding = BLOCK_SIZE - input.length % BLOCK_SIZE;
        byte[] output = new byte[input.length + padding];
        System.arraycopy(input, 0, output, 0, input.length);
        for (int i = input.length; i < output.length; i++) output[i] = (byte) padding;
        return output;
    }

    private static byte[] unpad(byte[] input) {
        int padding = input[input.length - 1] & 0xff;
        if (padding <= 0 || padding > input.length) throw new IllegalArgumentException("填充长度非法");
        byte[] output = new byte[input.length - padding];
        System.arraycopy(input, 0, output, 0, output.length);
        return output;
    }
}
