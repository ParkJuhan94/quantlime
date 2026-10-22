package com.quantlime.backtest.controller;

import com.quantlime.backtest.service.BacktestService;
import com.quantlime.batch.backtest.BacktestBatchLauncher;
import com.quantlime.batch.common.BatchLaunchRejectedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
 * 노출한다(FeedCollectionAdminController와 동일 패턴). 2026-10-22부로
 * prepare-dataset/run-universe/cross-sectional은 요청을 붙잡지 않고 Spring Batch
 * Job(batch 모듈)을 시작해 executionId를 202로 돌려준다 - 주간 스케줄러
 * (BacktestWeeklyScheduler)와 같은 Job이다. 상태 조회와 restart는
 * DataOpsAdminController의 /api/admin/data/batch/executions/** 를 쓴다.
 */
@Tag(name = "백테스트 관리자 API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/backtest")
public class BacktestAdminController {

    private final BacktestService backtestService;
    private final BacktestBatchLauncher backtestBatchLauncher;

    @PostMapping("/prepare-dataset")
    @Operation(summary = "[트리거2] 백테스트용 데이터 일괄 준비(비동기 배치)",
        description = "국내/해외 유니버스(거래대금 상위 500) 선정+백필과 벤치마크 지수"
            + "(KOSPI/KOSDAQ/NASDAQ/SP500) 백필을 Spring Batch Job으로 시작하고 executionId를 202로 바로 돌려준다. "
            + "진행 상황은 GET /api/admin/data/batch/executions/{id}. 다른 백테스트 Job이 실행 중이면 409.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<String> prepareDataset() {
        log.info("[admin] 백테스트 데이터셋 준비 수동 트리거 요청");
        return launch("백테스트 데이터셋 준비", backtestBatchLauncher::launchDataset);
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
    @Operation(summary = "유니버스 전체 백테스트 수동 트리거(비동기 배치)",
        description = "데이터셋 준비 후 국내+해외 거래대금 상위 500씩 전체에 대해 백테스트를 실행하는 Job을 시작하고 "
            + "executionId를 202로 바로 돌려준다. 오늘 이미 실행된 종목은 스킵(force=true면 스킵 없이 전부 재실행). "
            + "축×horizon마다 block bootstrap 500회라 무거운 연산이며 진행 상황은 "
            + "GET /api/admin/data/batch/executions/{id}, 실패하면 POST .../restart로 끊긴 종목부터 이어 한다. "
            + "다른 백테스트 Job이 실행 중이면 409.")
    public ResponseEntity<String> runUniverse(@RequestParam(defaultValue = "false") boolean force) {
        log.info("[admin] 유니버스 백테스트 수동 트리거 요청: force={}", force);
        return launch("유니버스 백테스트", () -> backtestBatchLauncher.launchUniverse(force));
    }

    @PostMapping("/cross-sectional")
    @Operation(summary = "횡단면(cross-sectional) Rank IC 백테스트 수동 트리거(비동기 배치)",
        description = "같은 날짜의 여러 종목을 줄세워 비교한다. 이미 저장된 backtest_daily_score를 "
            + "그대로 쓰므로 run-universe가 먼저 실행돼 있어야 한다. market 생략 시 4개 시장"
            + "(KOSPI/KOSDAQ/NASDAQ/NYSE) 전부, 지정 시 그 시장만. nullTest=true면 조합마다 "
            + "순환이동 널 분포까지 계산해 훨씬 오래 걸린다 - nullRepeats(기본 200)로 반복 "
            + "횟수를 조절할 수 있다. Job을 시작하고 executionId를 202로 바로 돌려주며, 시장은 기본적으로 "
            + "순차 실행된다(backtest-batch.parallelism=1). 병렬로 올리면 시장들이 서킷브레이커를 "
            + "공유하니 엔진 워커와 메모리를 먼저 확인할 것. 다른 백테스트 Job이 실행 중이면 409.")
    public ResponseEntity<String> runCrossSectional(
            @RequestParam(required = false) String market,
            @RequestParam String scoreVersion,
            @RequestParam(defaultValue = "false") boolean nullTest,
            @RequestParam(defaultValue = "200") int nullRepeats) {
        log.info("[admin] 횡단면 백테스트 수동 트리거 요청: market={}, scoreVersion={}, nullTest={}, nullRepeats={}",
            market, scoreVersion, nullTest, nullRepeats);
        if (market != null && !market.isBlank() && !isSupportedMarket(market)) {
            return ResponseEntity.badRequest().body("지원하지 않는 시장입니다: " + market);
        }
        return launch("횡단면 백테스트",
            () -> backtestBatchLauncher.launchCrossSectional(market, scoreVersion, nullTest, nullRepeats));
    }

    private static boolean isSupportedMarket(String market) {
        return List.of("KOSPI", "KOSDAQ", "NASDAQ", "NYSE").contains(market);
    }

    private ResponseEntity<String> launch(String label, Supplier<Long> launcher) {
        try {
            long executionId = launcher.get();
            return ResponseEntity.accepted().body("%s 시작: executionId=%d".formatted(label, executionId));
        } catch (BatchLaunchRejectedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
