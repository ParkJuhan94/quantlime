package com.quantlab.common.controller;

import com.quantlab.auth.dto.mapper.AuthMapper;
import com.quantlab.auth.dto.response.TokenResponse;
import com.quantlab.auth.jwt.JwtTokenProvider;
import com.quantlab.auth.token.RefreshTokenStore;
import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.price.scheduler.OhlcvCollectorScheduler;
import com.quantlab.price.service.DailyPriceService;
import com.quantlab.score.service.ScoreService;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.dto.StockMasterSyncResult;
import com.quantlab.stock.service.StockMasterService;
import com.quantlab.stock.service.StockMasterSyncService;
import com.quantlab.user.domain.OAuthProvider;
import com.quantlab.user.domain.User;
import com.quantlab.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final DailyPriceService dailyPriceService;
    private final ScoreService scoreService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

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

    @PostMapping("/scores/recalculate")
    @Operation(summary = "[개발용] 관심 종목 전체 스코어 일괄 재계산 수동 트리거")
    public ResponseEntity<String> triggerScoreRecalculate() {
        scoreService.recalculateWatchlistedScores();
        return ResponseEntity.ok("스코어 재계산 완료");
    }

    @PostMapping("/auth/token")
    @Operation(summary = "[개발용] 실제 소셜 로그인 없이 테스트 사용자 JWT 발급")
    public ResponseEntity<TokenResponse> issueDevToken() {
        OAuthUserInfo devUserInfo = new OAuthUserInfo(
            OAuthProvider.GOOGLE, DEV_TEST_PROVIDER_ID,
            "dev@test.local", "개발테스트유저", null);
        User user = userService.findOrCreate(devUserInfo);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenStore.save(user.getId(), refreshToken);

        return ResponseEntity.ok(AuthMapper.toTokenResponse(
            accessToken, refreshToken, jwtTokenProvider.getAccessTokenValidity()));
    }
}
