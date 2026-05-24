package com.sovereigncomm.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenService {
    private final SecureRandom secureRandom = new SecureRandom();

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
}
