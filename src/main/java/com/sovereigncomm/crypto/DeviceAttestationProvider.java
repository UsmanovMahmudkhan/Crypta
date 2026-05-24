package com.sovereigncomm.crypto;

import java.util.Map;

public interface DeviceAttestationProvider {
    boolean verifyAttestation(byte[] attestationObject, Map<String, Object> expectedClaims);
}
