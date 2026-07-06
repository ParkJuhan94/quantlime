package com.quantlab.score.controller;

import com.quantlab.auth.resolver.LoginUser;
import com.quantlab.score.dto.response.ScoreRankingResponse;
import com.quantlab.score.dto.response.ScoreResponse;
import com.quantlab.score.service.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "스코어 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ScoreController {

    private final ScoreService scoreService;

    @GetMapping("/stocks/{stockCode}/score")
    @Operation(
        summary = "종목 스코어 조회",
        description = "가장 최근에 계산된 종목의 추세추종/평균회귀 스코어와 등급을 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<ScoreResponse> getScore(@PathVariable String stockCode) {
        return ResponseEntity.ok(scoreService.getScore(stockCode));
    }

    @GetMapping("/dashboard/scores")
    @Operation(
        summary = "관심 종목 전체 스코어 랭킹 조회",
        description = "로그인 사용자의 관심 종목 중 스코어가 계산된 종목을 종합점수 내림차순으로 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<ScoreRankingResponse>> getDashboardScores(
        @LoginUser Long userId) {
        return ResponseEntity.ok(scoreService.getDashboardScores(userId));
    }
}
