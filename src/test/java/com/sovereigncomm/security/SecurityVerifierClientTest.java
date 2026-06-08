package com.sovereigncomm.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class SecurityVerifierClientTest {
    @Test
    void localKeyTransparencyFallbackIsDeterministicAndChainsPreviousHead() {
        SecurityVerifierClient client = new SecurityVerifierClient(new TokenService("unit-pepper"), RestClient.builder(), "");
        String canonical = Base64.getEncoder().encodeToString(new TokenService("unit-pepper").sha256("entry"));
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        SecurityVerifierClient.KeyTransparencyAppendRequest request = new SecurityVerifierClient.KeyTransparencyAppendRequest(
                orgId, userId, deviceId, canonical, "", 0);

        SecurityVerifierClient.KeyTransparencyAppendResponse first = client.appendKeyTransparencyEntry(request);
        SecurityVerifierClient.KeyTransparencyAppendResponse second = client.appendKeyTransparencyEntry(request);
        SecurityVerifierClient.KeyTransparencyAppendResponse chained = client.appendKeyTransparencyEntry(
                new SecurityVerifierClient.KeyTransparencyAppendRequest(orgId, userId, deviceId, canonical, first.signedTreeHeadBase64(), 1));

        assertThat(first.signedTreeHeadBase64()).isEqualTo(second.signedTreeHeadBase64());
        assertThat(chained.signedTreeHeadBase64()).isNotEqualTo(first.signedTreeHeadBase64());
        assertThat(first.inclusionProof()).containsEntry("mode", "local_hash_chain_fallback");
        assertThat(first.proofVersion()).isEqualTo("verifier-proof-v1");
    }

    @Test
    void localDeviceAttestationFallbackRejectsBlankAttestation() {
        SecurityVerifierClient client = new SecurityVerifierClient(new TokenService("unit-pepper"), RestClient.builder(), "");

        SecurityVerifierClient.DeviceAttestationVerificationResponse response = client.verifyDeviceAttestation(
                new SecurityVerifierClient.DeviceAttestationVerificationRequest(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "android-key", " "));

        assertThat(response.valid()).isFalse();
        assertThat(response.verificationStatus()).isEqualTo("REJECTED");
        assertThat(response.verifiedClaims()).containsEntry("mode", "local-fallback");
    }

    @Test
    void remoteHealthUsesVerifierEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecurityVerifierClient client = new SecurityVerifierClient(new TokenService("unit-pepper"), builder, "https://verifier.example");
        server.expect(requestTo("https://verifier.example/healthz"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"status\":\"UP\",\"detail\":\"remote verifier\"}", MediaType.APPLICATION_JSON));

        SecurityVerifierClient.VerifierHealth health = client.health();

        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.detail()).isEqualTo("remote verifier");
        server.verify();
    }

    @Test
    void localSignedAdminActionFallbackRequiresSignatureField() {
        SecurityVerifierClient client = new SecurityVerifierClient(new TokenService("unit-pepper"), RestClient.builder(), "");

        SecurityVerifierClient.SignedAdminActionVerificationResponse rejected = client.verifySignedAdminAction(
                new SecurityVerifierClient.SignedAdminActionVerificationRequest(UUID.randomUUID(), "{\"actionType\":\"LOCKDOWN\"}"));
        SecurityVerifierClient.SignedAdminActionVerificationResponse accepted = client.verifySignedAdminAction(
                new SecurityVerifierClient.SignedAdminActionVerificationRequest(UUID.randomUUID(), "{\"signatureBase64\":\"abc\"}"));

        assertThat(rejected.valid()).isFalse();
        assertThat(rejected.reason()).isEqualTo("SIGNATURE_REQUIRED");
        assertThat(accepted.valid()).isTrue();
        assertThat(accepted.reason()).isEqualTo("LOCAL_SIGNATURE_FIELD_PRESENT");
    }
}
