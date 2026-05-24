package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.AttachmentCreateRequest;
import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.service.AttachmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    IdResponse createUpload(@Valid @RequestBody AttachmentCreateRequest request) {
        return attachmentService.createEncryptedAttachment(request);
    }

