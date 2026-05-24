package com.sovereigncomm.security;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PlaintextGuard {
    private static final Pattern FORBIDDEN_PLAINTEXT_KEY = Pattern.compile("(?i).*(plaintext|message_body|file_plaintext|decrypted_metadata).*");

    public void rejectPlaintextShapedMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (FORBIDDEN_PLAINTEXT_KEY.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("Plaintext-like field is not accepted by the backend: " + entry.getKey());
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedMap = new LinkedHashMap<>();
                nested.forEach((k, v) -> nestedMap.put(String.valueOf(k), v));
                rejectPlaintextShapedMetadata(nestedMap);
            }
        }
    }
}
