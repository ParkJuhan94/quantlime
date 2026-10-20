package com.quantlime.notice.controller;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.notice.dto.response.NoticeResponse;
import com.quantlime.notice.dto.response.NoticeSummaryResponse;
import com.quantlime.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 공지 열람은 로그인 없이도 가능해야 한다(SecurityConfig의 GET /api/notices/** permitAll).
@Tag(name = "공지사항 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    @Operation(summary = "공지 목록 조회(최신순)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<NoticeSummaryResponse>> getNotices(Pageable pageable) {
        return ResponseEntity.ok(noticeService.getNotices(pageable));
    }

    @GetMapping("/{noticeId}")
    @Operation(summary = "공지 상세 조회")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<NoticeResponse> getNotice(@PathVariable Long noticeId) {
        return ResponseEntity.ok(noticeService.getNotice(noticeId));
    }
}
