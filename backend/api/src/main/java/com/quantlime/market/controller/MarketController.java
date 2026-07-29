package com.quantlime.market.controller;

import com.quantlime.auth.resolver.OptionalLoginUser;
import com.quantlime.market.dto.response.IndexChartResponse;
import com.quantlime.market.dto.response.IndexMinuteChartResponse;
import com.quantlime.market.dto.response.MarketIndexResponse;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.market.service.MarketIndexService;
import com.quantlime.market.service.MarketRankingService;
import com.quantlime.watchlist.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final WatchlistService watchlistService;

    @GetMapping("/indices")
    @Operation(
        summary = "주요 지수 위젯 조회",
        description = "환율(USD/KRW)·비트코인·코스피/코스닥 현재가를 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<MarketIndexResponse> getIndices() {
        return ResponseEntity.ok(marketIndexService.getIndices());
    }

    @GetMapping("/indices/{code}/chart")
    @Operation(
        summary = "지수 일봉 차트 조회",
        description = "코스피/코스닥/나스닥/S&P500/SOXX 최근 일봉 이력을 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<IndexChartResponse>> getIndexChart(
        @PathVariable
        @Pattern(regexp = "KOSPI|KOSDAQ|NASDAQ|SP500|SOXX",
            message = "code는 KOSPI, KOSDAQ, NASDAQ, SP500, SOXX 중 하나여야 합니다.") String code) {
        return ResponseEntity.ok(marketIndexService.getIndexChart(code));
    }

    @GetMapping("/indices/{code}/minute-chart")
    @Operation(
        summary = "지수 당일 분봉 차트 조회",
        description = "코스피/코스닥 당일(휴장이면 최근 거래일) 1분봉 이력을 조회한다(해외지수는 분봉 미제공)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<IndexMinuteChartResponse>> getIndexMinuteChart(
        @PathVariable
        @Pattern(regexp = "KOSPI|KOSDAQ", message = "code는 KOSPI 또는 KOSDAQ여야 합니다.") String code) {
        return ResponseEntity.ok(marketIndexService.getIndexMinuteChart(code));
    }

    @GetMapping("/indices/bitcoin/minute-chart")
    @Operation(
        summary = "비트코인 최근 24시간 차트 조회",
        description = "Upbit 30분봉 48개(24시간)를 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<IndexMinuteChartResponse>> getBitcoinChart() {
        return ResponseEntity.ok(marketIndexService.getBitcoinChart());
    }

    @GetMapping("/indices/usdkrw/chart")
    @Operation(
        summary = "달러 환율 일봉 차트 조회",
        description = "원/달러(USD/KRW) 최근 일별 종가 이력을 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<IndexChartResponse>> getExchangeRateChart() {
        return ResponseEntity.ok(marketIndexService.getExchangeRateChart());
    }

    @GetMapping("/ranking")
    @Operation(
        summary = "전종목 급등락/거래대금/거래량 실시간 랭킹 조회",
        description = "지정한 시장(scope)의 종목을 정렬 기준(sort)으로 정렬한 상위 N개를 조회한다(장중에만 갱신). "
            + "scope=overseas는 Toss 랭킹 API(최대 100건) 기반이라 watchlistOnly=true여도 top100 밖의 "
            + "관심종목은 gainers/losers일 때만 자체 계산으로 걸러진다(amount/volume은 top100 이내로 제한). "
            + "watchlistOnly=true면 로그인한 사용자의 관심종목만 걸러 정렬한다(비로그인이면 빈 배열)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<MarketRankingResponse>> getRanking(
        @RequestParam(defaultValue = "domestic")
        @Pattern(regexp = "domestic|overseas", message = "scope는 domestic 또는 overseas여야 합니다.") String scope,
        @RequestParam(defaultValue = "gainers")
        @Pattern(regexp = "gainers|losers|amount|volume",
            message = "sort는 gainers, losers, amount, volume 중 하나여야 합니다.") String sort,
        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
        @Max(value = 50, message = "limit는 50 이하여야 합니다.") int limit,
        @RequestParam(defaultValue = "false") boolean watchlistOnly,
        @OptionalLoginUser Long userId) {
        if (watchlistOnly && userId == null) {
            return ResponseEntity.ok(List.of());
        }
        Set<String> watchlistCodes = watchlistOnly ? watchlistService.getWatchlistStockCodes(userId) : null;
        return ResponseEntity.ok(marketRankingService.getRanking(scope, sort, limit, watchlistCodes));
    }
}
