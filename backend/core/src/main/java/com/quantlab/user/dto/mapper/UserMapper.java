package com.quantlab.user.dto.mapper;

import com.quantlab.user.domain.User;
import com.quantlab.user.dto.response.UserMeResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public class UserMapper {

    public static UserMeResponse toUserMeResponse(User user) {
        return new UserMeResponse(user.getNickname(), user.getEmail(), user.getProfileImageUrl());
    }
}
