package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.AttachmentCreateRequest;
import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.service.AttachmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
