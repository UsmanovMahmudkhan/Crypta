package com.sovereigncomm.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaintextGuardTest {
    private final PlaintextGuard guard = new PlaintextGuard();

    @Test
    void acceptsCiphertextMetadata() {
        assertThatCode(() -> guard.rejectPlaintextShapedMetadata(Map.of(
                "algorithm", "XChaCha20-Poly1305",
                "associatedData", Map.of("roomId", "room-1"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNestedPlaintextMetadataKeys() {
        assertThatThrownBy(() -> guard.rejectPlaintextShapedMetadata(Map.of(
                "crypto", Map.of("decrypted_metadata", "not allowed"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decrypted_metadata");
    }
}
