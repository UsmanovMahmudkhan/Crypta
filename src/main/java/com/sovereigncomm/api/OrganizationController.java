package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.api.dto.CommonDtos.OrganizationCreateRequest;
import com.sovereigncomm.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;
