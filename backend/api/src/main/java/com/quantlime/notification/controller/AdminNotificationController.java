package com.quantlime.notification.controller;

import com.quantlime.auth.resolver.LoginUser;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.dto.request.BroadcastNotificationRequest;
import com.quantlime.notification.service.FcmPushService;
import com.quantlime.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// SecurityConfig의 "/api/admin/**" -> hasRole("ADMIN") 규칙이 이미 이
// 컨트롤러 전체를 관리자 전용으로 막고 있다(/api/admin/feed/** 등과 동일
// 패턴) - 별도 인가 코드 불필요.
@Tag(name = "관리자 알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final FcmPushService fcmPushService;
    private final UserService userService;

    @PostMapping("/broadcast")
    @Operation(summary = "전체 사용자 공지 발송")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> broadcast(@Valid @RequestBody BroadcastNotificationRequest request) {
        List<Long> allUserIds = userService.getAllUserIds();
        fcmPushService.sendToUsers(allUserIds, NotificationType.ADMIN_NOTICE,
            request.title(), request.content(), request.linkUrl());
        return ResponseEntity.accepted().build();
    }

    // 운영에서 전체 사용자에게 보내기 전에 내용과 푸시 도착을 본인 계정으로만 확인하는 용도 - 대상을 고를 수
    // 없는 전용 엔드포인트라, broadcast에 대상 지정 파라미터를 두는 방식과 달리 실수로 전체 발송될 일이 없다.
    @PostMapping("/test")
    @Operation(summary = "내 계정에게만 테스트 공지 발송")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> sendTest(@LoginUser Long userId,
                                         @Valid @RequestBody BroadcastNotificationRequest request) {
        fcmPushService.sendToUser(userId, NotificationType.ADMIN_NOTICE,
            request.title(), request.content(), request.linkUrl());
        return ResponseEntity.accepted().build();
    }
}
