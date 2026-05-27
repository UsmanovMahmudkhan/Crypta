package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.AttachmentCreateRequest;
import com.sovereigncomm.api.dto.CommonDtos.DeliveryReceiptRequest;
import com.sovereigncomm.api.dto.CommonDtos.EncryptedMessageSubmitRequest;
import com.sovereigncomm.api.dto.CommonDtos.UserRegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommonDtosValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidMessageKindAndHashShape() {
        EncryptedMessageSubmitRequest request = new EncryptedMessageSubmitRequest(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PLAINTEXT",
                "Y2lwaGVydGV4dA==",
                "too-short",
                Map.of("algorithm", "DEMO"));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("messageKind", "ciphertextSha256");
    }

    @Test
    void acceptsValidDirectMessageEnvelopeShape() {
        EncryptedMessageSubmitRequest request = new EncryptedMessageSubmitRequest(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DIRECT",
                "Y2lwaGVydGV4dA==",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                Map.of("algorithm", "DEMO"));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsUnsafeAttachmentAndReceiptInputs() {
        AttachmentCreateRequest attachment = new AttachmentCreateRequest(
                UUID.randomUUID(),
                "demo/file.bin",
                "invalid",
                0,
                Map.of("algorithm", "DEMO"));
        DeliveryReceiptRequest receipt = new DeliveryReceiptRequest(UUID.randomUUID(), UUID.randomUUID(), "OPENED");

        assertThat(validator.validate(attachment)).hasSize(2);
        assertThat(validator.validate(receipt))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("receiptType");
    }

    @Test
    void rejectsUnsupportedRolesBeforeServiceNormalization() {
        UserRegisterRequest request = new UserRegisterRequest(
                UUID.randomUUID(),
                "person@example.com",
                "Person",
                List.of("MEMBER", "ROOT"));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("roles[1].<list element>");
    }
}
