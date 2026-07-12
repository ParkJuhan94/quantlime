package com.quantlab.market.controller;

import com.quantlab.market.dto.response.MarketIndexResponse;
import com.quantlab.market.dto.response.MarketRankingResponse;
import com.quantlab.market.service.MarketIndexService;
import com.quantlab.market.service.MarketRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "시장 정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market")
public class MarketController {

    private final MarketIndexService marketIndexService;
    private final MarketRankingService marketRankingService;

    @GetMapping("/indices")
    @Operation(
        summary = "주요 지수 위젯 조회",
        description = "환율(USD/KRW)과 비트코인 현재가를 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<MarketIndexResponse> getIndices() {
        return ResponseEntity.ok(marketIndexService.getIndices());
    }

    @GetMapping("/ranking")
    @Operation(
        summary = "전종목 급등락 실시간 랭킹 조회",
        description = "전 상장 종목을 등락률 기준으로 정렬한 상위 N개를 조회한다(장중에만 갱신)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<MarketRankingResponse>> getRanking(
        @RequestParam(defaultValue = "gainers")
        @Pattern(regexp = "gainers|losers", message = "sort는 gainers 또는 losers여야 합니다.") String sort,
        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
        @Max(value = 50, message = "limit는 50 이하여야 합니다.") int limit) {
        return ResponseEntity.ok(marketRankingService.getRanking(sort, limit));
    }
}
