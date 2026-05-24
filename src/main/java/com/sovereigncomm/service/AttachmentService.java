package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.AttachmentCreateRequest;
import com.sovereigncomm.api.dto.CommonDtos.IdResponse;

import java.util.Map;
import java.util.UUID;

public interface AttachmentService {
    IdResponse createEncryptedAttachment(AttachmentCreateRequest request);
    Map<String, Object> createEncryptedDownload(UUID attachmentId);
}
