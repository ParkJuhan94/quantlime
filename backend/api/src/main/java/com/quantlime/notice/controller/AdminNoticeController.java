package com.quantlime.notice.controller;

import com.quantlime.notice.dto.request.CreateNoticeRequest;
import com.quantlime.notice.dto.request.UpdateNoticeRequest;
import com.quantlime.notice.dto.response.CreateNoticeResponse;
import com.quantlime.notice.dto.response.NoticeResponse;
import com.quantlime.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// SecurityConfig의 "/api/admin/**" -> hasRole("ADMIN") 규칙이 이 컨트롤러 전체를 관리자 전용으로 막는다.
@Tag(name = "관리자 공지사항 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final NoticeService noticeService;

    @PostMapping
    @Operation(summary = "공지 등록", description = "sendNotification=true면 등록과 함께 전체 사용자에게 /notices/{id} 링크 알림을 보낸다")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    public ResponseEntity<CreateNoticeResponse> create(@Valid @RequestBody CreateNoticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noticeService.create(request));
    }

    @PutMapping("/{noticeId}")
    @Operation(summary = "공지 수정", description = "알림은 다시 보내지 않는다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<NoticeResponse> update(@PathVariable Long noticeId,
                                                 @Valid @RequestBody UpdateNoticeRequest request) {
        return ResponseEntity.ok(noticeService.update(noticeId, request));
    }

    @DeleteMapping("/{noticeId}")
    @Operation(summary = "공지 삭제", description = "이미 나간 알림의 링크는 삭제된 공지를 가리키게 된다(화면이 안내함)")
    public ResponseEntity<Void> delete(@PathVariable Long noticeId) {
        noticeService.delete(noticeId);
        return ResponseEntity.noContent().build();
    }
}
