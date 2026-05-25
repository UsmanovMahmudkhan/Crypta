package com.sovereigncomm.security;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class TokenService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] tokenPepper;

    public TokenService(@Value("${app.security.token-pepper:}") String tokenPepper) {
        this.tokenPepper = tokenPepper == null ? new byte[0] : tokenPepper.getBytes(StandardCharsets.UTF_8);
    }

    public String newToken() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return "sc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public String newChallenge() {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);
    }

    public byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public byte[] sessionTokenHash(String token) {
        if (tokenPepper.length == 0) {
            return sha256(token);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenPepper, "HmacSHA256"));
            return mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
