package com.quantlime.notification.controller;

import com.quantlime.auth.resolver.LoginUser;
import com.quantlime.common.dto.PageResponse;
import com.quantlime.notification.dto.request.RegisterFcmTokenRequest;
import com.quantlime.notification.dto.response.NotificationResponse;
import com.quantlime.notification.dto.response.UnreadCountResponse;
import com.quantlime.notification.service.FcmTokenService;
import com.quantlime.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final FcmTokenService fcmTokenService;

    @PostMapping("/fcm-tokens")
    @Operation(summary = "FCM 토큰 등록")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> registerFcmToken(
        @LoginUser Long userId, @Valid @RequestBody RegisterFcmTokenRequest request) {
        fcmTokenService.registerToken(userId, request.token(), request.deviceInfo());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/fcm-tokens")
    @Operation(summary = "FCM 토큰 삭제(로그아웃 시 호출)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> deleteFcmToken(@RequestParam String token) {
        fcmTokenService.deleteToken(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "알림 목록 조회(최근 생성순)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<NotificationResponse>> getNotifications(
        @LoginUser Long userId, Pageable pageable) {
        return ResponseEntity.ok(notificationService.getNotifications(userId, pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "안읽은 알림 개수 조회")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<UnreadCountResponse> countUnread(@LoginUser Long userId) {
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.countUnread(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 단건 읽음 처리")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> markAsRead(@LoginUser Long userId, @PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "알림 전체 읽음 처리")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> markAllAsRead(@LoginUser Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
}
