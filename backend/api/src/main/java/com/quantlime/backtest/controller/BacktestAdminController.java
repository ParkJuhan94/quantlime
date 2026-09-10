package com.quantlime.backtest.controller;

import com.quantlime.backtest.service.BacktestDatasetPreparationService;
import com.quantlime.backtest.service.BacktestService;
import com.quantlime.backtest.service.BacktestUniverseService;
import com.quantlime.backtest.service.CrossSectionalBacktestService;
import com.quantlime.stock.domain.MarketType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ROLE_ADMIN만 호출 가능(SecurityConfig의 /api/admin/** 매처 참고). 기존
 * DevController(/dev/backtest/**)가 @Profile("dev")로 가드돼 있어 prod
 * 백엔드에는 애초에 존재하지 않았다 - 이관 이후 backtest_result/
 * backtest_daily_score가 0건으로 비어있는 걸 발견해(2026-09-10), prod에서도
 * 수동 트리거 가능하도록 같은 서비스 빈들을 재사용해 별도 컨트롤러로
 * 노출한다(FeedCollectionAdminController와 동일 패턴). 정규 자동 트리거는
 * 여전히 없다 - run-universe/cross-sectional 둘 다 무거운 연산(각각 수십 분
 * 이상 걸릴 수 있음)이라 스케줄러에 얹지 않고 수동 실행으로 남겨둔
 * DevController 시절의 판단을 그대로 따른다.
 */
@Tag(name = "백테스트 관리자 API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/backtest")
public class BacktestAdminController {

    private final BacktestDatasetPreparationService backtestDatasetPreparationService;
    private final BacktestService backtestService;
    private final BacktestUniverseService backtestUniverseService;
    private final CrossSectionalBacktestService crossSectionalBacktestService;

    @PostMapping("/prepare-dataset")
    @Operation(summary = "[트리거2] 백테스트용 데이터 일괄 준비",
        description = "국내/해외 유니버스(거래대금 상위 500) 선정+백필과 벤치마크 지수"
            + "(KOSPI/KOSDAQ/NASDAQ/SP500) 백필을 한 번에 실행한다.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<String> prepareDataset() {
        log.info("[admin] 백테스트 데이터셋 준비 수동 트리거 시작");
        backtestDatasetPreparationService.prepareDataset();
        log.info("[admin] 백테스트 데이터셋 준비 수동 트리거 완료");
        return ResponseEntity.ok("백테스트 데이터셋 준비 완료");
    }

    @PostMapping("/run")
    @Operation(summary = "종목 백테스트 수동 트리거", description = "국내 KOSPI/KOSDAQ, 해외 NASDAQ/NYSE 지원")
    public ResponseEntity<String> runBacktest(@RequestParam String stockCode) {
        log.info("[admin] 종목 백테스트 수동 트리거 시작: stockCode={}", stockCode);
        backtestService.runBacktest(stockCode);
        log.info("[admin] 종목 백테스트 수동 트리거 완료: stockCode={}", stockCode);
        return ResponseEntity.ok("백테스트 완료: " + stockCode);
    }

    @PostMapping("/run-universe")
    @Operation(summary = "유니버스 전체 백테스트 수동 트리거",
        description = "국내+해외 거래대금 상위 500씩 전체에 대해 백테스트를 순회 실행한다. "
            + "오늘 이미 실행된 종목은 스킵(force=true면 스킵 없이 전부 재실행). 축×horizon마다 "
            + "block bootstrap 500회라 무거운 연산 - 수십 분 이상 걸릴 수 있어 HTTP 응답을 "
            + "기다리지 못할 수 있다(nginx/브라우저 타임아웃). 응답이 끊겨도 서버 쪽 처리는 "
            + "계속 진행되니, 완료 여부는 /api/admin/backtest 로그나 backtest_daily_score "
            + "건수로 확인할 것.")
    public ResponseEntity<String> runUniverse(@RequestParam(defaultValue = "false") boolean force) {
        log.info("[admin] 유니버스 백테스트 수동 트리거 시작(무거운 연산, 수십 분 이상 소요될 수 있음): force={}", force);
        backtestUniverseService.runUniverse(force);
        log.info("[admin] 유니버스 백테스트 수동 트리거 완료");
        return ResponseEntity.ok("유니버스 백테스트 완료");
    }

    @PostMapping("/cross-sectional")
    @Operation(summary = "횡단면(cross-sectional) Rank IC 백테스트 수동 트리거",
        description = "같은 날짜의 여러 종목을 줄세워 비교한다. 이미 저장된 backtest_daily_score를 "
            + "그대로 쓰므로 run-universe가 먼저 실행돼 있어야 한다. market 생략 시 4개 시장"
            + "(KOSPI/KOSDAQ/NASDAQ/NYSE) 전부, 지정 시 그 시장만. nullTest=true면 조합마다 "
            + "순환이동 널 분포까지 계산해 훨씬 오래 걸린다 - nullRepeats(기본 200)로 반복 "
            + "횟수를 조절할 수 있다. 여러 시장을 동시에 여러 요청으로 병렬 호출하지 말 것"
            + "(서킷브레이커를 공유해 한쪽이 느려지면 나머지가 전부 실패한다).")
    public ResponseEntity<String> runCrossSectional(
            @RequestParam(required = false) String market,
            @RequestParam String scoreVersion,
            @RequestParam(defaultValue = "false") boolean nullTest,
            @RequestParam(defaultValue = "200") int nullRepeats) {
        log.info("[admin] 횡단면 백테스트 수동 트리거 시작: market={}, scoreVersion={}, nullTest={}, nullRepeats={}",
            market, scoreVersion, nullTest, nullRepeats);
        if (market == null) {
            crossSectionalBacktestService.runAllMarkets(scoreVersion, nullTest, nullRepeats);
        } else {
            crossSectionalBacktestService.runForMarket(
                MarketType.valueOf(market), scoreVersion, nullTest, nullRepeats);
        }
        log.info("[admin] 횡단면 백테스트 수동 트리거 완료");
        return ResponseEntity.ok("횡단면 백테스트 완료");
    }
}
