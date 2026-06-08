package com.fongmi.quickjs.utils;

import android.util.Base64;

import com.github.catvod.utils.Util;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {

    public static String md5(String text) {
        try {
            return Util.md5(text);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String aes(String mode, boolean encrypt, String input, boolean inBase64, String key, String iv, boolean outBase64) {
        try {
            byte[] keyBuf = key.getBytes();
            if (keyBuf.length < 16) keyBuf = Arrays.copyOf(keyBuf, 16);
            byte[] ivBuf = iv == null ? new byte[0] : iv.getBytes();
            if (ivBuf.length < 16) ivBuf = Arrays.copyOf(ivBuf, 16);
            Cipher cipher = Cipher.getInstance(mode + "Padding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBuf, "AES");
            if (iv == null) cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec);
            else cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivBuf));
            byte[] inBuf = inBase64 ? Base64.decode(input.replaceAll("_", "/").replaceAll("-", "+"), Base64.DEFAULT) : input.getBytes(StandardCharsets.UTF_8);
            return outBase64 ? Base64.encodeToString(cipher.doFinal(inBuf), Base64.NO_WRAP) : new String(cipher.doFinal(inBuf), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String rsa(String mode, boolean pub, boolean encrypt, String input, boolean inBase64, String key, boolean outBase64) {
        try {
            Key rsaKey = generateKey(pub, key);
            byte[] inBytes = inBase64 ? Base64.decode(input.replaceAll("_", "/").replaceAll("-", "+"), Base64.DEFAULT) : input.getBytes(StandardCharsets.UTF_8);
            String tranformation = "RSA/ECB/PKCS1Padding";
            if ("RSA/PKCS1".equals(mode)) tranformation = "RSA/ECB/PKCS1Padding";
            else if ("RSA/None/NoPadding".equals(mode)) tranformation = "RSA/None/NoPadding";
            Cipher cipher = Cipher.getInstance(tranformation);
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, rsaKey);
            byte[] outBytes = cipher.doFinal(inBytes);
            return outBase64 ? Base64.encodeToString(outBytes, Base64.NO_WRAP) : new String(outBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static Key generateKey(boolean pub, String key) throws Exception {
        if (pub) key = key.replaceAll("[\\r\\n]", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
        else key = key.replaceAll("[\\r\\n]", "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "");
        return pub ? KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT))) : KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(key, Base64.DEFAULT)));
    }
}
