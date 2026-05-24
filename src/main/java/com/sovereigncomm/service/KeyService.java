package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.KeyBundleResponse;
import com.sovereigncomm.api.dto.CommonDtos.PublicKeyUploadRequest;

import java.util.UUID;

public interface KeyService {
    void uploadIdentityKey(PublicKeyUploadRequest request);
