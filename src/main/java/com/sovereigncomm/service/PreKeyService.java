package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.PreKeyUploadRequest;

public interface PreKeyService {
    void uploadSignedPreKey(PreKeyUploadRequest request);
    void uploadOneTimePreKey(PreKeyUploadRequest request);
    void uploadPqPreKey(PreKeyUploadRequest request);
}
