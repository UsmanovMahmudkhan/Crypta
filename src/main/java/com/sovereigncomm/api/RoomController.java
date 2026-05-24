package com.sovereigncomm.api;

import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.api.dto.CommonDtos.MemberChangeRequest;
import com.sovereigncomm.api.dto.CommonDtos.RoomCreateRequest;
import com.sovereigncomm.api.dto.CommonDtos.RoomPolicyUpdateRequest;
import com.sovereigncomm.service.MLSGroupService;
import com.sovereigncomm.service.RoomPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mission-rooms")
public class RoomController {
    private final MLSGroupService mlsGroupService;
    private final RoomPolicyService roomPolicyService;

