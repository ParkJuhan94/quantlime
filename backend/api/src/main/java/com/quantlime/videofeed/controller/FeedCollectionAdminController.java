package com.quantlime.videofeed.controller;

import com.quantlime.videofeed.dto.CollectResult;
import com.quantlime.videofeed.service.ChannelVelocityInitializationService;
import com.quantlime.videofeed.service.FeedCollectionFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ROLE_ADMIN만 호출 가능(SecurityConfig의 /api/admin/** 매처 참고).
 * 정규 실행 경로는 FeedCollectionScheduler(하루 3회)이고, 이 엔드포인트는
 * 그 사이에 수동으로 즉시 실행하고 싶을 때 쓴다.
 */
@Tag(name = "피드 수집 관리자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/feed")
public class FeedCollectionAdminController {

    private final FeedCollectionFacade feedCollectionFacade;
    private final ChannelVelocityInitializationService channelVelocityInitializationService;

    @PostMapping("/collect")
    @Operation(summary = "전체 채널 영상 수집 수동 트리거", description = "채널별 수집→적재→필터링을 즉시 실행한다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<CollectResult>> collect() {
        return ResponseEntity.ok(feedCollectionFacade.runAll());
    }

    @PostMapping("/channels/{channelId}/velocity/initialize")
    @Operation(summary = "채널 중앙값 업로드 속도 초기 산정", description = "최근 30개 업로드의 views/hours 중앙값을 계산해 저장한다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<BigDecimal> initializeVelocity(@PathVariable Long channelId) {
        return ResponseEntity.ok(channelVelocityInitializationService.initializeMedianVelocity(channelId));
    }
}
