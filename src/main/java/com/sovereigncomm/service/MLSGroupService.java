package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.api.dto.CommonDtos.MemberChangeRequest;
import com.sovereigncomm.api.dto.CommonDtos.RoomCreateRequest;

public interface MLSGroupService {
    IdResponse createMissionRoom(RoomCreateRequest request);
    void inviteMember(MemberChangeRequest request);
    void removeMember(MemberChangeRequest request);
}
