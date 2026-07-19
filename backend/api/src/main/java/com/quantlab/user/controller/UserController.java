package com.quantlab.user.controller;

import com.quantlab.auth.resolver.LoginUser;
import com.quantlab.user.dto.mapper.UserMapper;
import com.quantlab.user.dto.response.UserMeResponse;
import com.quantlab.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<UserMeResponse> getMe(@LoginUser Long userId) {
        UserMeResponse response = UserMapper.toUserMeResponse(userService.getById(userId));
        return ResponseEntity.ok(response);
    }
}
