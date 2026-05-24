package com.sovereigncomm.service;

import com.sovereigncomm.api.dto.CommonDtos.IdResponse;
import com.sovereigncomm.api.dto.CommonDtos.UserRegisterRequest;

public interface UserService {
    IdResponse registerUser(UserRegisterRequest request);
}
