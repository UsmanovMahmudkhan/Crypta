package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.api.dto.CommonDtos.OrganizationCreateRequest;

public interface OrganizationService {
    IdResponse createOrganization(OrganizationCreateRequest request);
}
