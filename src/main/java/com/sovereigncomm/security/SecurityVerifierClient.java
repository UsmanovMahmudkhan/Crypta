package com.sovereigncomm.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SecurityVerifierClient {
    private final TokenService tokenService;
    private final RestClient restClient;
    private final boolean remoteEnabled;

    public SecurityVerifierClient(
            TokenService tokenService,
            RestClient.Builder restClientBuilder,
            @Value("${app.security.verifier.base-url:}") String verifierBaseUrl) {
        this.tokenService = tokenService;
        this.remoteEnabled = verifierBaseUrl != null && !verifierBaseUrl.isBlank();
        this.restClient = remoteEnabled ? restClientBuilder.baseUrl(verifierBaseUrl).build() : null;
    }

    public boolean remoteEnabled() {
        return remoteEnabled;
    }

    public VerifierHealth health() {
        if (!remoteEnabled) {
            return new VerifierHealth("LOCAL_FALLBACK", "embedded deterministic verifier fallback");
        }
        return restClient.get()
                .uri("/healthz")
                .retrieve()
                .body(VerifierHealth.class);
    }

    public KeyTransparencyAppendResponse appendKeyTransparencyEntry(KeyTransparencyAppendRequest request) {
        if (remoteEnabled) {
            return restClient.post()
                    .uri("/v1/key-transparency/append")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(KeyTransparencyAppendResponse.class);
        }
        return localAppend(request);
    }

    public DeviceAttestationVerificationResponse verifyDeviceAttestation(DeviceAttestationVerificationRequest request) {
        if (remoteEnabled) {
            return restClient.post()
                    .uri("/v1/device-attestations/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(DeviceAttestationVerificationResponse.class);
        }
        boolean accepted = request.attestationObjectBase64() != null && !request.attestationObjectBase64().isBlank();
        return new DeviceAttestationVerificationResponse(
                accepted,
                accepted ? "PENDING_EXTERNAL_VERIFICATION" : "REJECTED",
                false,
                false,
                Map.of("mode", "local-fallback"));
    }

    public AuditVerificationResponse verifyAuditChain(AuditVerificationRequest request) {
        if (remoteEnabled) {
            return restClient.post()
                    .uri("/v1/audit/verify-chain")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AuditVerificationResponse.class);
        }
        return new AuditVerificationResponse(true, request.eventCount(), "LOCAL_FALLBACK");
    }

    public SignedAdminActionVerificationResponse verifySignedAdminAction(SignedAdminActionVerificationRequest request) {
        if (remoteEnabled) {
            return restClient.post()
                    .uri("/v1/admin-actions/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SignedAdminActionVerificationResponse.class);
        }
        boolean accepted = request.signedActionEnvelopeJson() != null
                && request.signedActionEnvelopeJson().contains("\"signatureBase64\"");
        return new SignedAdminActionVerificationResponse(accepted, accepted ? "LOCAL_SIGNATURE_FIELD_PRESENT" : "SIGNATURE_REQUIRED");
    }

    private KeyTransparencyAppendResponse localAppend(KeyTransparencyAppendRequest request) {
        byte[] previous = decodeOrEmpty(request.previousTreeHeadBase64());
        byte[] canonical = decodeOrEmpty(request.canonicalEntryHashBase64());
        byte[] index = tokenService.sha256(Long.toString(request.logIndex()));
        byte[] leaf = tokenService.sha256(concat(previous, canonical, index));
        byte[] sth = tokenService.sha256(concat("crypta-local-sth-v1".getBytes(), leaf, previous));
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("mode", "local_hash_chain_fallback");
        proof.put("leafHashBase64", Base64.getEncoder().encodeToString(leaf));
        proof.put("previousTreeHeadBase64", request.previousTreeHeadBase64());
        return new KeyTransparencyAppendResponse(
                Base64.getEncoder().encodeToString(sth),
                Base64.getEncoder().encodeToString(leaf),
                proof,
                "",
                "local-" + UUID.nameUUIDFromBytes(sth),
                "verifier-proof-v1");
    }

    private byte[] decodeOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(value);
    }

    private byte[] concat(byte[]... values) {
        int size = 0;
        for (byte[] value : values) {
            size += value.length;
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, out, offset, value.length);
            offset += value.length;
        }
        return out;
    }

    public record VerifierHealth(String status, String detail) {
    }

    public record KeyTransparencyAppendRequest(UUID organizationId, UUID subjectUserId, UUID subjectDeviceId,
                                               String canonicalEntryHashBase64, String previousTreeHeadBase64,
                                               long logIndex) {
    }

    public record KeyTransparencyAppendResponse(String signedTreeHeadBase64, String merkleLeafHashBase64,
                                                Map<String, Object> inclusionProof, String consistencyProofBase64,
                                                String checkpointId, String proofVersion) {
    }

    public record DeviceAttestationVerificationRequest(UUID organizationId, UUID userId, UUID deviceId,
                                                       String format, String attestationObjectBase64) {
    }

    public record DeviceAttestationVerificationResponse(boolean valid, String verificationStatus,
                                                        boolean hardwareBacked, boolean strongBoxOrSecureEnclave,
                                                        Map<String, Object> verifiedClaims) {
    }

    public record AuditVerificationRequest(UUID organizationId, int eventCount, String latestEventHashBase64) {
    }

    public record AuditVerificationResponse(boolean valid, int verifiedEvents, String checkpointId) {
    }

    public record SignedAdminActionVerificationRequest(UUID organizationId, String signedActionEnvelopeJson) {
    }

    public record SignedAdminActionVerificationResponse(boolean valid, String reason) {
    }
}
