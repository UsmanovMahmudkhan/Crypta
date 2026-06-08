package com.sovereigncomm.security;

import org.junit.jupiter.api.Test;

import java.util.List;
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

    @Test
    void rejectsPlaintextKeysInsideCollectionValues() {
        assertThatThrownBy(() -> guard.rejectPlaintextShapedMetadata(Map.of(
                "metadataSet", List.of(Map.of("message_body", "not allowed")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message_body");
    }

    @Test
    void rejectsPlaintextKeysInsideArrayValues() {
        assertThatThrownBy(() -> guard.rejectPlaintextShapedMetadata(Map.of(
                "policyResults", new Object[]{Map.of("file_plaintext", "not allowed")})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file_plaintext");
    }

    @Test
    void rejectsPlaintextKeysInsideMixedCollectionAndMapStructures() {
        assertThatThrownBy(() -> guard.rejectPlaintextShapedMetadata(Map.of(
                "outer", List.of(Map.of(
                        "inner", List.of(Map.of("plaintextPreview", "not allowed")))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaintextPreview");
    }

    @Test
    void rejectsPlaintextKeysWhenScanningArbitraryValues() {
        assertThatThrownBy(() -> guard.rejectPlaintextShapedValue(List.of(
                Map.of("crypto", Map.of("decrypted_metadata", "not allowed")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decrypted_metadata");
    }
}
