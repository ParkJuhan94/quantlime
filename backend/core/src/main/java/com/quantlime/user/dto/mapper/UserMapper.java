package com.quantlime.user.dto.mapper;

import com.quantlime.user.domain.User;
import com.quantlime.user.dto.response.UserMeResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public class UserMapper {

    public static UserMeResponse toUserMeResponse(User user) {
        return new UserMeResponse(user.getNickname(), user.getEmail(), user.getProfileImageUrl());
    }
}
