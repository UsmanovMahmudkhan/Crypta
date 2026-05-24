package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.KeyBundleResponse;
import com.sovereigncomm.api.dto.CommonDtos.PreKeyUploadRequest;
import com.sovereigncomm.api.dto.CommonDtos.PublicKeyUploadRequest;
import com.sovereigncomm.service.KeyService;
import com.sovereigncomm.service.PreKeyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

