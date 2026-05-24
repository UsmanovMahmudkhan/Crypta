package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.api.dto.CommonDtos.MemberChangeRequest;
import com.sovereigncomm.api.dto.CommonDtos.RoomCreateRequest;
import com.sovereigncomm.api.dto.CommonDtos.RoomPolicyUpdateRequest;
import com.sovereigncomm.service.MLSGroupService;
import com.sovereigncomm.service.RoomPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
