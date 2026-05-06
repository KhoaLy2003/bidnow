package com.bidnow.identity.mapper;

import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.dto.response.RegisterResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "id", target = "userId")
    RegisterResponse toRegisterResponse(User user);
}
