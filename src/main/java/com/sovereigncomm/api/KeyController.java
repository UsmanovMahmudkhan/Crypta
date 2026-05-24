package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.KeyBundleResponse;
import com.sovereigncomm.api.dto.CommonDtos.PreKeyUploadRequest;
import com.sovereigncomm.api.dto.CommonDtos.PublicKeyUploadRequest;
import com.sovereigncomm.service.KeyService;
import com.sovereigncomm.service.PreKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
