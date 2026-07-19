package com.quantlab.common.controller;

import com.quantlab.auth.cookie.RefreshTokenCookieProvider;
import com.quantlab.auth.dto.mapper.AuthMapper;
import com.quantlab.auth.dto.response.TokenResponse;
import com.quantlab.auth.jwt.JwtTokenProvider;
import com.quantlab.auth.token.RefreshTokenStore;
import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.market.service.BenchmarkIndexBackfillService;
import com.quantlab.market.service.DomesticUniverseSelectionService;
import com.quantlab.market.service.OverseasUniverseSelectionService;
import com.quantlab.price.scheduler.OhlcvCollectorScheduler;
import com.quantlab.price.service.DailyPriceService;
import com.quantlab.price.service.OverseasDailyPriceBackfillService;
import com.quantlab.score.service.ScoreService;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.dto.StockMasterSyncResult;
import com.quantlab.stock.service.OverseasStockMasterSyncService;
import com.quantlab.stock.service.StockMasterService;
import com.quantlab.stock.service.StockMasterSyncService;
import com.quantlab.user.domain.OAuthProvider;
import com.quantlab.user.domain.User;
import com.quantlab.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "개발용 API")
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev")
public class DevController {

    private static final String DEV_TEST_PROVIDER_ID = "dev-test-user";

    private final OhlcvCollectorScheduler ohlcvCollectorScheduler;
    private final StockMasterService stockMasterService;
    private final StockMasterSyncService stockMasterSyncService;
    private final OverseasStockMasterSyncService overseasStockMasterSyncService;
    private final DailyPriceService dailyPriceService;
    private final OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;
    private final BenchmarkIndexBackfillService benchmarkIndexBackfillService;
    private final DomesticUniverseSelectionService domesticUniverseSelectionService;
    private final OverseasUniverseSelectionService overseasUniverseSelectionService;
    private final ScoreService scoreService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/ohlcv/collect")
    @Operation(summary = "[개발용] OHLCV 수집 수동 트리거")
    public ResponseEntity<String> triggerOhlcvCollect() {
        ohlcvCollectorScheduler.collectDailyOhlcv();
        return ResponseEntity.ok("OHLCV 수집 완료");
    }

    @PostMapping("/ohlcv/backfill")
    @Operation(summary = "[개발용] 전체 상장 종목 이력 OHLCV 일괄 백필(종목당 200일)")
    public ResponseEntity<String> triggerBackfill() {
        for (Stock stock : stockMasterService.getAllListedStocks()) {
            try {
                dailyPriceService.backfillHistoryIfNeeded(stock.getStockCode());
            } catch (Exception e) {
                log.error("백필 실패: stockCode={}, error={}",
                    stock.getStockCode(), e.getMessage(), e);
            }
        }
        return ResponseEntity.ok("백필 완료");
    }

    @PostMapping("/stock-master/sync")
    @Operation(summary = "[개발용] 종목마스터 동기화(신규상장/상장폐지 반영) 수동 트리거")
    public ResponseEntity<String> triggerStockMasterSync() {
        StockMasterSyncResult result = stockMasterSyncService.syncStockMaster();
        return ResponseEntity.ok(
            "종목마스터 동기화 완료: 신규상장 %d건, 상장폐지 %d건"
                .formatted(result.newlyListedCount(), result.delistedCount()));
    }

    @PostMapping("/benchmark/backfill")
    @Operation(summary = "[개발용] 백테스트 벤치마크 지수(KOSPI/KOSDAQ) 이력 백필 수동 트리거")
    public ResponseEntity<String> triggerBenchmarkBackfill() {
        benchmarkIndexBackfillService.backfillAllIfNeeded();
        return ResponseEntity.ok("벤치마크 지수 백필 완료");
    }

    @PostMapping("/stock-master/overseas/sync")
    @Operation(summary = "[개발용] 해외 종목마스터(NASDAQ/NYSE) 동기화 수동 트리거")
    public ResponseEntity<String> triggerOverseasStockMasterSync() {
        overseasStockMasterSyncService.syncAll();
        return ResponseEntity.ok("해외 종목마스터 동기화 완료");
    }

    @PostMapping("/universe/domestic/select")
    @Operation(summary = "[개발용] 백테스트 국내 유니버스(거래대금 상위 500, REIT 제외) 2-pass 선정+백필 수동 트리거")
    public ResponseEntity<String> triggerDomesticUniverseSelection() {
        List<String> selected = domesticUniverseSelectionService.selectAndBackfillUniverse();
        return ResponseEntity.ok("국내 유니버스 선정+백필 완료: %d종목".formatted(selected.size()));
    }

    @PostMapping("/overseas/backfill")
    @Operation(summary = "[개발용] 해외 종목 단건 이력 백필 수동 트리거")
    public ResponseEntity<String> triggerOverseasBackfill(
            @RequestParam String stockCode,
            @RequestParam String exchangeCode,
            @RequestParam(defaultValue = "60") int targetDays) {
        overseasDailyPriceBackfillService.backfillHistoryIfNeeded(stockCode, exchangeCode, targetDays);
        return ResponseEntity.ok("해외 종목 백필 완료: " + stockCode);
    }

    @PostMapping("/universe/overseas/select")
    @Operation(summary = "[개발용] 백테스트 해외 유니버스(거래대금 상위 500, NASDAQ/NYSE) 2-pass 선정+백필 수동 트리거")
    public ResponseEntity<String> triggerOverseasUniverseSelection() {
        List<String> selected = overseasUniverseSelectionService.selectAndBackfillUniverse();
        return ResponseEntity.ok("해외 유니버스 선정+백필 완료: %d종목".formatted(selected.size()));
    }

    @PostMapping("/scores/recalculate")
    @Operation(summary = "[개발용] 전 상장 종목 스코어 일괄 재계산 수동 트리거")
    public ResponseEntity<String> triggerScoreRecalculate() {
        scoreService.recalculateAllListedScores();
        return ResponseEntity.ok("스코어 재계산 완료");
    }

    @PostMapping("/auth/token")
    @Operation(summary = "[개발용] 실제 소셜 로그인 없이 테스트 사용자 JWT 발급"
        + "(리프레시 토큰은 실제 로그인과 동일하게 httpOnly 쿠키로 내려온다)")
    public ResponseEntity<TokenResponse> issueDevToken() {
        OAuthUserInfo devUserInfo = new OAuthUserInfo(
            OAuthProvider.GOOGLE, DEV_TEST_PROVIDER_ID,
            "dev@test.local", "개발테스트유저", null);
        User user = userService.findOrCreate(devUserInfo);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenStore.save(user.getId(), refreshToken);

        ResponseCookie cookie = refreshTokenCookieProvider.create(
            refreshToken, jwtTokenProvider.getRefreshTokenValidity());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(AuthMapper.toTokenResponse(accessToken, jwtTokenProvider.getAccessTokenValidity()));
    }
}
