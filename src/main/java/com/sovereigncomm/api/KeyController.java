package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.KeyBundleResponse;
import com.sovereigncomm.api.dto.CommonDtos.KeyTransparencyProofResponse;
import com.sovereigncomm.api.dto.CommonDtos.PreKeyUploadRequest;
import com.sovereigncomm.api.dto.CommonDtos.PublicKeyUploadRequest;
import com.sovereigncomm.service.KeyService;
import com.sovereigncomm.service.KeyTransparencyService;
import com.sovereigncomm.service.PreKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/keys")
public class KeyController {
    private final KeyService keyService;
    private final PreKeyService preKeyService;
    private final KeyTransparencyService keyTransparencyService;

    public KeyController(KeyService keyService, PreKeyService preKeyService, KeyTransparencyService keyTransparencyService) {
        this.keyService = keyService;
        this.preKeyService = preKeyService;
        this.keyTransparencyService = keyTransparencyService;
    }

    @PostMapping("/identity")
    void uploadIdentityKey(@Valid @RequestBody PublicKeyUploadRequest request) {
        keyService.uploadIdentityKey(request);
    }

    @PostMapping("/signed-prekeys")
    void uploadSignedPreKey(@Valid @RequestBody PreKeyUploadRequest request) {
        preKeyService.uploadSignedPreKey(request);
    }

    @PostMapping("/one-time-prekeys")
    void uploadOneTimePreKey(@Valid @RequestBody PreKeyUploadRequest request) {
        preKeyService.uploadOneTimePreKey(request);
    }

    @PostMapping("/pq-prekeys")
    void uploadPqPreKey(@Valid @RequestBody PreKeyUploadRequest request) {
        preKeyService.uploadPqPreKey(request);
    }

    @GetMapping("/bundle/{userId}")
    KeyBundleResponse keyBundle(@PathVariable UUID userId) {
        return keyService.fetchKeyBundle(userId);
    }

    @GetMapping("/transparency/{userId}")
    KeyTransparencyProofResponse keyTransparencyProof(@PathVariable UUID userId) {
        return keyTransparencyService.proofResponse(userId);
    }
}
