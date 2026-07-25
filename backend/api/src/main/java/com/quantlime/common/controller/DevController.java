package com.quantlime.common.controller;

import com.quantlime.auth.cookie.RefreshTokenCookieProvider;
import com.quantlime.auth.dto.mapper.AuthMapper;
import com.quantlime.auth.dto.response.TokenResponse;
import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.auth.token.RefreshTokenStore;
import com.quantlime.backtest.service.BacktestDatasetPreparationService;
import com.quantlime.backtest.service.BacktestService;
import com.quantlime.backtest.service.BacktestUniverseService;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.stock.dto.StockMasterSyncResult;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import com.quantlime.stock.service.StockMasterSyncService;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private final StockMasterSyncService stockMasterSyncService;
    private final OverseasStockMasterSyncService overseasStockMasterSyncService;
    private final MarketDataRefreshService marketDataRefreshService;
    private final BacktestDatasetPreparationService backtestDatasetPreparationService;
    private final BacktestService backtestService;
    private final BacktestUniverseService backtestUniverseService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/stock-master/sync")
    @Operation(summary = "[개발용] 종목마스터 동기화(신규상장/상장폐지 반영) 수동 트리거")
    public ResponseEntity<String> triggerStockMasterSync() {
        StockMasterSyncResult result = stockMasterSyncService.syncStockMaster();
        return ResponseEntity.ok(
            "종목마스터 동기화 완료: 신규상장 %d건, 상장폐지 %d건"
                .formatted(result.newlyListedCount(), result.delistedCount()));
    }

    @PostMapping("/stock-master/overseas/sync")
    @Operation(summary = "[개발용] 해외 종목마스터(NASDAQ/NYSE) 동기화 수동 트리거")
    public ResponseEntity<String> triggerOverseasStockMasterSync() {
        overseasStockMasterSyncService.syncAll();
        return ResponseEntity.ok("해외 종목마스터 동기화 완료");
    }

    @PostMapping("/refresh")
    @Operation(summary = "[개발용/트리거1] 전 상장종목(국내+해외) 가격+스코어를 마지막 저장일 "
        + "다음날부터 오늘까지만 갭필. stockCode를 주면 그 종목만 처리(디버깅용). "
        + "매일 16:00 배치·로컬 기동 시 자동 캐치업과 동일한 로직을 공유한다.")
    public ResponseEntity<String> triggerRefresh(
            @RequestParam(required = false) String stockCode) {
        if (stockCode != null) {
            marketDataRefreshService.refreshStock(stockCode);
            return ResponseEntity.ok("가격/스코어 갱신 완료: " + stockCode);
        }
        marketDataRefreshService.refreshAll();
        return ResponseEntity.ok("전종목 가격/스코어 갱신 완료");
    }

    @PostMapping("/backtest/prepare-dataset")
    @Operation(summary = "[개발용/트리거2] 백테스트용 데이터 일괄 준비 - 국내/해외 유니버스"
        + "(거래대금 상위 500) 선정+백필과 벤치마크 지수(KOSPI/KOSDAQ/NASDAQ/SP500) 백필을 한 번에 실행")
    public ResponseEntity<String> triggerBacktestDatasetPreparation() {
        backtestDatasetPreparationService.prepareDataset();
        return ResponseEntity.ok("백테스트 데이터셋 준비 완료");
    }

    @PostMapping("/backtest/run")
    @Operation(summary = "[개발용] 종목 백테스트 수동 트리거(국내 KOSPI/KOSDAQ, 해외 NASDAQ/NYSE 지원)")
    public ResponseEntity<String> triggerBacktest(@RequestParam String stockCode) {
        backtestService.runBacktest(stockCode);
        return ResponseEntity.ok("백테스트 완료: " + stockCode);
    }

    @PostMapping("/backtest/run-universe")
    @Operation(summary = "[개발용] 백테스트용 유니버스(국내+해외 거래대금 상위 500씩) 전체에 대해 "
        + "백테스트를 순회 실행. 오늘 이미 실행된 종목은 스킵. 트리거1과 달리 자동(기동 시/16:00) "
        + "트리거에는 연결돼 있지 않음(축×horizon마다 block bootstrap 500회라 무거운 연산 - 수동으로만 실행)")
    public ResponseEntity<String> triggerBacktestUniverse() {
        backtestUniverseService.runUniverse();
        return ResponseEntity.ok("유니버스 백테스트 완료");
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
