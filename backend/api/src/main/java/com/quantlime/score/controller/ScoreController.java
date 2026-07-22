package com.quantlime.score.controller;

import com.quantlime.auth.resolver.OptionalLoginUser;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.dto.response.ScoreResponse;
import com.quantlime.score.service.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
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
        summary = "스코어 랭킹 조회",
        description = "watchlistOnly=true(기본값)면 로그인 사용자의 관심 종목만, false면 전 상장종목 상위 N개를 "
            + "종합점수 내림차순으로 조회한다(watchlistOnly=true인데 비로그인이면 빈 배열, 실시간 랭킹 '스코어' 탭용)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<ScoreRankingResponse>> getDashboardScores(
        @RequestParam(defaultValue = "true") boolean watchlistOnly,
        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
        @Max(value = 50, message = "limit는 50 이하여야 합니다.") int limit,
        @OptionalLoginUser Long userId) {
        if (watchlistOnly) {
            if (userId == null) {
                return ResponseEntity.ok(List.of());
            }
            return ResponseEntity.ok(scoreService.getDashboardScores(userId));
        }
        return ResponseEntity.ok(scoreService.getAllStocksScoreRanking(limit));
    }
}
