package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.KeyTransparencyProofResponse;

import java.util.Map;
import java.util.UUID;

public interface KeyTransparencyService {
    void appendKeyEvent(UUID deviceId, String canonicalEntryJson, String entrySignatureBase64);
    Map<String, Object> proof(UUID userId);
    KeyTransparencyProofResponse proofResponse(UUID userId);
}
