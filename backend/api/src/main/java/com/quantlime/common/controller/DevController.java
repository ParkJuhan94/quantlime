package com.quantlime.common.controller;

import com.quantlime.auth.cookie.RefreshTokenCookieProvider;
import com.quantlime.auth.dto.mapper.AuthMapper;
import com.quantlime.auth.dto.response.TokenResponse;
import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.auth.token.RefreshTokenStore;
import com.quantlime.backtest.service.BacktestDatasetPreparationService;
import com.quantlime.backtest.service.BacktestService;
import com.quantlime.backtest.service.BacktestUniverseService;
import com.quantlime.backtest.service.CrossSectionalBacktestService;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.price.dto.PriceJumpReport;
import com.quantlime.price.service.DailyPriceIntegrityService;
import com.quantlime.price.service.DailyPriceResettlementService;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.dto.StockMasterSyncResult;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import com.quantlime.stock.service.DomesticStockMasterSyncService;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
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

    private final DomesticStockMasterSyncService domesticStockMasterSyncService;
    private final OverseasStockMasterSyncService overseasStockMasterSyncService;
    private final MarketDataRefreshService marketDataRefreshService;
    private final BacktestDatasetPreparationService backtestDatasetPreparationService;
    private final BacktestService backtestService;
    private final BacktestUniverseService backtestUniverseService;
    private final CrossSectionalBacktestService crossSectionalBacktestService;
    private final DailyPriceResettlementService dailyPriceResettlementService;
    private final DailyPriceIntegrityService dailyPriceIntegrityService;
    private final ScoreService scoreService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/stock-master/sync")
    @Operation(summary = "[개발용] 종목마스터 동기화(신규상장/상장폐지 반영) 수동 트리거")
    public ResponseEntity<String> triggerStockMasterSync() {
        log.info("[dev] 국내 종목마스터 동기화 수동 트리거 시작");
        StockMasterSyncResult result = domesticStockMasterSyncService.syncStockMaster();
        log.info("[dev] 국내 종목마스터 동기화 수동 트리거 완료: 신규상장={}건, 상장폐지={}건",
            result.newlyListedCount(), result.delistedCount());
        return ResponseEntity.ok(
            "종목마스터 동기화 완료: 신규상장 %d건, 상장폐지 %d건"
                .formatted(result.newlyListedCount(), result.delistedCount()));
    }

    @PostMapping("/stock-master/overseas/sync")
    @Operation(summary = "[개발용] 해외 종목마스터(NASDAQ/NYSE) 동기화 수동 트리거")
    public ResponseEntity<String> triggerOverseasStockMasterSync() {
        log.info("[dev] 해외 종목마스터 동기화 수동 트리거 시작");
        overseasStockMasterSyncService.syncAll();
        log.info("[dev] 해외 종목마스터 동기화 수동 트리거 완료");
        return ResponseEntity.ok("해외 종목마스터 동기화 완료");
    }

    @PostMapping("/refresh")
    @Operation(summary = "[개발용/트리거1] 전 상장종목(국내+해외) 가격+스코어를 마지막 저장일 "
        + "다음날부터 오늘까지만 갭필. stockCode를 주면 그 종목만 처리(디버깅용). "
        + "매일 16:00 배치·로컬 기동 시 자동 캐치업과 동일한 로직을 공유한다.")
    public ResponseEntity<String> triggerRefresh(
            @RequestParam(required = false) String stockCode) {
        if (stockCode != null) {
            log.info("[dev] 가격/스코어 갱신 수동 트리거 시작(단건): stockCode={}", stockCode);
            marketDataRefreshService.refreshStock(stockCode);
            log.info("[dev] 가격/스코어 갱신 수동 트리거 완료(단건): stockCode={}", stockCode);
            return ResponseEntity.ok("가격/스코어 갱신 완료: " + stockCode);
        }
        // 전종목(국내+해외 약 9천개) 갭필이라 수 분~수십 분이 걸릴 수 있다 -
        // 이 로그가 없으면 그 사이 쏟아지는 레이트리밋/미지원심볼 에러 로그가
        // "왜 지금 갑자기" 발생했는지 추적할 방법이 없다(2026-07-30 실제로
        // 겪은 혼란 - 수동 트리거로 인한 결과인지 스케줄러/기동 캐치업
        // 때문인지 로그만 봐선 구분 불가했음).
        log.info("[dev] 가격/스코어 갱신 수동 트리거 시작(전종목)");
        return marketDataRefreshService.refreshAllExclusively()
            .map(result -> {
                log.info("[dev] 가격/스코어 갱신 수동 트리거 완료(전종목)");
                return ResponseEntity.ok("전종목 가격/스코어 갱신 완료");
            })
            .orElseGet(() -> {
                log.info("[dev] 가격/스코어 갱신 수동 트리거 스킵 - 이미 다른 실행(스케줄러/기동 캐치업) 중");
                return ResponseEntity.ok("이미 다른 실행이 갱신 중입니다 - 스킵");
            });
    }

    @PostMapping("/prices/resettle")
    @Operation(summary = "[개발용/복구] 장중 스냅샷 영구 고정 버그 복구 - 전 상장종목의 from~오늘 구간을 "
        + "무조건 재조회해 덮어쓴다. 재확정 윈도우 도입 이후의 신규 오염은 정기 스윕이 자동으로 막지만, "
        + "이미 그 윈도우 밖으로 밀려난 기존 오염은 이 트리거로만 복구된다.")
    public ResponseEntity<String> triggerPriceResettlement(
            @RequestParam String market, @RequestParam String from) {
        LocalDate fromDate = LocalDate.parse(from);
        log.info("[dev] 가격 재확정 일괄 복구 수동 트리거 시작: market={}, from={}", market, fromDate);
        int processed = switch (market) {
            case "domestic" -> dailyPriceResettlementService.resettleDomestic(fromDate);
            case "overseas" -> dailyPriceResettlementService.resettleOverseas(fromDate);
            default -> dailyPriceResettlementService.resettleDomestic(fromDate)
                + dailyPriceResettlementService.resettleOverseas(fromDate);
        };
        log.info("[dev] 가격 재확정 일괄 복구 수동 트리거 완료: 처리종목수={}", processed);
        return ResponseEntity.ok("가격 재확정 복구 완료: 처리종목수=" + processed);
    }

    @PostMapping("/prices/repair-adjusted")
    @Operation(summary = "[개발용/복구] 수정주가 소급 재조정(액면분할/병합) 버그 복구 - from~오늘 구간에서 "
        + "가격제한폭을 넘는 종가 점프를 스캔하고, dryRun=false면 걸린 종목만 전 구간 재백필한다. "
        + "dryRun=true(기본값)면 API 호출 없이 스캔 결과만 반환한다.")
    public ResponseEntity<List<PriceJumpReport>> triggerAdjustedPriceRepair(
            @RequestParam String from,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        LocalDate fromDate = LocalDate.parse(from);
        log.info("[dev] 수정주가 재조정 복구 수동 트리거 시작: from={}, dryRun={}", fromDate, dryRun);
        List<PriceJumpReport> jumps = dailyPriceIntegrityService.repairDomesticAdjusted(fromDate, dryRun);
        log.info("[dev] 수정주가 재조정 복구 수동 트리거 완료: 발견={}건, dryRun={}", jumps.size(), dryRun);
        return ResponseEntity.ok(jumps);
    }

    @PostMapping("/scores/rebuild")
    @Operation(summary = "[개발용/복구] 가격 소급 복구 후 오염된 과거 스코어 재구성 - from 이후 스코어를 "
        + "전부 지우고 전 상장종목을 다시 계산한다. 외부 API(Toss) 호출은 없고 퀀트 엔진 호출만 발생한다.")
    public ResponseEntity<String> triggerScoreRebuild(@RequestParam String from) {
        LocalDate fromDate = LocalDate.parse(from);
        log.info("[dev] 스코어 재구성 수동 트리거 시작: from={}", fromDate);
        scoreService.rebuildScoresFrom(fromDate);
        log.info("[dev] 스코어 재구성 수동 트리거 완료: from={}", fromDate);
        return ResponseEntity.ok("스코어 재구성 완료: from=" + fromDate);
    }

    @PostMapping("/backtest/prepare-dataset")
    @Operation(summary = "[개발용/트리거2] 백테스트용 데이터 일괄 준비 - 국내/해외 유니버스"
        + "(거래대금 상위 500) 선정+백필과 벤치마크 지수(KOSPI/KOSDAQ/NASDAQ/SP500) 백필을 한 번에 실행")
    public ResponseEntity<String> triggerBacktestDatasetPreparation() {
        log.info("[dev] 백테스트 데이터셋 준비 수동 트리거 시작");
        backtestDatasetPreparationService.prepareDataset();
        log.info("[dev] 백테스트 데이터셋 준비 수동 트리거 완료");
        return ResponseEntity.ok("백테스트 데이터셋 준비 완료");
    }

    @PostMapping("/backtest/run")
    @Operation(summary = "[개발용] 종목 백테스트 수동 트리거(국내 KOSPI/KOSDAQ, 해외 NASDAQ/NYSE 지원)")
    public ResponseEntity<String> triggerBacktest(@RequestParam String stockCode) {
        log.info("[dev] 종목 백테스트 수동 트리거 시작: stockCode={}", stockCode);
        backtestService.runBacktest(stockCode);
        log.info("[dev] 종목 백테스트 수동 트리거 완료: stockCode={}", stockCode);
        return ResponseEntity.ok("백테스트 완료: " + stockCode);
    }

    @PostMapping("/backtest/run-universe")
    @Operation(summary = "[개발용] 백테스트용 유니버스(국내+해외 거래대금 상위 500씩) 전체에 대해 "
        + "백테스트를 순회 실행. 오늘 이미 실행된 종목은 스킵(force=true면 스킵 없이 전부 재실행 - "
        + "가격 소급 복구 직후처럼 오늘 이미 돌았어도 반드시 다시 돌려야 하는 경우용). 트리거1과 달리 "
        + "자동(기동 시/16:00) 트리거에는 연결돼 있지 않음(축×horizon마다 block bootstrap 500회라 "
        + "무거운 연산 - 수동으로만 실행)")
    public ResponseEntity<String> triggerBacktestUniverse(
            @RequestParam(defaultValue = "false") boolean force) {
        // 축×horizon마다 block bootstrap 500회 x 최대 1000종목이라 수십 분
        // 이상 걸릴 수 있는 무거운 연산 - 시작 로그가 없으면 그 시간 동안
        // 서버가 멈춘 건지 정상 진행 중인지 로그만으로 알 수 없다.
        log.info("[dev] 유니버스 백테스트 수동 트리거 시작(무거운 연산, 수십 분 이상 소요될 수 있음): force={}", force);
        backtestUniverseService.runUniverse(force);
        log.info("[dev] 유니버스 백테스트 수동 트리거 완료");
        return ResponseEntity.ok("유니버스 백테스트 완료");
    }

    @PostMapping("/backtest/cross-sectional")
    @Operation(summary = "[개발용] 횡단면(cross-sectional) Rank IC 백테스트 수동 트리거 - "
        + "종목 하나의 시간축 안에서만 상관을 재는 /backtest/run-universe와 달리, 같은 날짜의 "
        + "여러 종목을 줄세워 비교한다(2026-08 감사 세션 - 둘의 결과가 반대 부호로 나온 걸 "
        + "발견해 도입, docs/CHANGELOG.md 참고). 이미 저장된 backtest_daily_score를 그대로 쓰므로 "
        + "run-universe가 먼저 실행돼 있어야 한다. market 생략 시 4개 시장(KOSPI/KOSDAQ/NASDAQ/NYSE) "
        + "전부, 지정 시 그 시장만(축2 x horizon4=8개 조합). nullTest=true면 조합마다 순환이동 "
        + "널 분포까지 계산해 훨씬 오래 걸린다(조합당 반복 200회).")
    public ResponseEntity<String> triggerCrossSectionalBacktest(
            @RequestParam(required = false) String market,
            @RequestParam String scoreVersion,
            @RequestParam(defaultValue = "false") boolean nullTest) {
        log.info("[dev] 횡단면 백테스트 수동 트리거 시작: market={}, scoreVersion={}, nullTest={}",
            market, scoreVersion, nullTest);
        if (market == null) {
            crossSectionalBacktestService.runAllMarkets(scoreVersion, nullTest);
        } else {
            crossSectionalBacktestService.runForMarket(MarketType.valueOf(market), scoreVersion, nullTest);
        }
        log.info("[dev] 횡단면 백테스트 수동 트리거 완료");
        return ResponseEntity.ok("횡단면 백테스트 완료");
    }

    @PostMapping("/auth/token")
    @Operation(summary = "[개발용] 실제 소셜 로그인 없이 테스트 사용자 JWT 발급"
        + "(리프레시 토큰은 실제 로그인과 동일하게 httpOnly 쿠키로 내려온다)")
    public ResponseEntity<TokenResponse> issueDevToken() {
        log.info("[dev] 개발용 테스트 사용자 JWT 발급 요청");
        OAuthUserInfo devUserInfo = new OAuthUserInfo(
            OAuthProvider.GOOGLE, DEV_TEST_PROVIDER_ID,
            "dev@test.local", "개발테스트유저", null);
        User user = userService.findOrCreate(devUserInfo);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenStore.save(user.getId(), refreshToken);

        ResponseCookie cookie = refreshTokenCookieProvider.create(
            refreshToken, jwtTokenProvider.getRefreshTokenValidity());
        log.info("[dev] 개발용 테스트 사용자 JWT 발급 완료: userId={}", user.getId());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(AuthMapper.toTokenResponse(accessToken, jwtTokenProvider.getAccessTokenValidity()));
    }
}
