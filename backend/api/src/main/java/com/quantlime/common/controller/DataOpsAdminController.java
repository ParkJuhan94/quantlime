package com.quantlime.common.controller;

import com.quantlime.batch.common.BatchExecutionView;
import com.quantlime.batch.common.BatchJobLaunchService;
import com.quantlime.batch.common.BatchLaunchRejectedException;
import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.price.dto.PriceJumpReport;
import com.quantlime.price.dto.RegularCloseBackfillResult;
import com.quantlime.price.service.DailyPriceIntegrityService;
import com.quantlime.price.service.DailyPriceResettlementService;
import com.quantlime.price.service.RegularCloseBackfillService;
import com.quantlime.stock.dto.StockMasterSyncResult;
import com.quantlime.stock.service.DomesticStockMasterSyncService;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영에서 시세·스코어 데이터가 어긋났을 때 수동으로 바로잡는 트리거. ROLE_ADMIN만 호출 가능(SecurityConfig의
 * /api/admin/** 매처 참고). DevController(/dev/**)는 @Profile("dev")라 prod 백엔드에 존재하지 않아, 같은 서비스 빈을
 * 재사용해 운영용으로 노출한다(BacktestAdminController와 동일 패턴). 로컬 스크립트와 문서가 /dev 경로를 쓰고 있어
 * DevController 쪽은 그대로 둔다.
 */
@Tag(name = "데이터 운영 관리자 API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/data")
public class DataOpsAdminController {

    private final DomesticStockMasterSyncService domesticStockMasterSyncService;
    private final OverseasStockMasterSyncService overseasStockMasterSyncService;
    private final MarketDataRefreshService marketDataRefreshService;
    private final DailyPriceResettlementService dailyPriceResettlementService;
    private final DailyPriceIntegrityService dailyPriceIntegrityService;
    private final RegularCloseBackfillService regularCloseBackfillService;
    private final BatchJobLaunchService batchJobLaunchService;

    @PostMapping("/stock-master/sync")
    @Operation(summary = "국내 종목마스터 동기화", description = "신규상장·상장폐지를 반영한다.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<String> syncDomesticStockMaster() {
        log.info("[admin] 국내 종목마스터 동기화 수동 트리거 시작");
        StockMasterSyncResult result = domesticStockMasterSyncService.syncStockMaster();
        log.info("[admin] 국내 종목마스터 동기화 수동 트리거 완료: 신규상장={}건, 상장폐지={}건",
            result.newlyListedCount(), result.delistedCount());
        return ResponseEntity.ok("종목마스터 동기화 완료: 신규상장 %d건, 상장폐지 %d건"
            .formatted(result.newlyListedCount(), result.delistedCount()));
    }

    @PostMapping("/stock-master/overseas/sync")
    @Operation(summary = "해외 종목마스터(NASDAQ/NYSE) 동기화")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<String> syncOverseasStockMaster() {
        log.info("[admin] 해외 종목마스터 동기화 수동 트리거 시작");
        overseasStockMasterSyncService.syncAll();
        log.info("[admin] 해외 종목마스터 동기화 수동 트리거 완료");
        return ResponseEntity.ok("해외 종목마스터 동기화 완료");
    }

    @PostMapping("/refresh")
    @Operation(summary = "가격+스코어 갭필",
        description = "전 상장종목(국내+해외)의 마지막 저장일 다음날부터 오늘까지를 채운다. stockCode를 주면 그 종목만 처리한다. "
            + "전종목은 수 분~수십 분이 걸려 HTTP 응답이 끊길 수 있으나(nginx 60초) 서버 처리는 계속된다. "
            + "스케줄러나 기동 캐치업이 이미 실행 중이면 스킵한다.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<String> refresh(@RequestParam(required = false) String stockCode) {
        if (stockCode != null) {
            log.info("[admin] 가격/스코어 갱신 수동 트리거 시작(단건): stockCode={}", stockCode);
            marketDataRefreshService.refreshStock(stockCode);
            log.info("[admin] 가격/스코어 갱신 수동 트리거 완료(단건): stockCode={}", stockCode);
            return ResponseEntity.ok("가격/스코어 갱신 완료: " + stockCode);
        }
        log.info("[admin] 가격/스코어 갱신 수동 트리거 시작(전종목)");
        return marketDataRefreshService.refreshAllExclusively()
            .map(result -> {
                log.info("[admin] 가격/스코어 갱신 수동 트리거 완료(전종목)");
                return ResponseEntity.ok("전종목 가격/스코어 갱신 완료");
            })
            .orElseGet(() -> {
                log.info("[admin] 가격/스코어 갱신 수동 트리거 스킵 - 이미 다른 실행 중");
                return ResponseEntity.ok("이미 다른 실행이 갱신 중입니다 - 스킵");
            });
    }

    @PostMapping("/prices/resettle")
    @Operation(summary = "가격 재확정 일괄 복구",
        description = "market(domestic/overseas, 그 외는 둘 다)의 전 상장종목에 대해 from~오늘 구간을 무조건 재조회해 덮어쓴다.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<String> resettlePrices(
            @RequestParam String market,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        log.info("[admin] 가격 재확정 일괄 복구 수동 트리거 시작: market={}, from={}", market, from);
        int processed = switch (market) {
            case "domestic" -> dailyPriceResettlementService.resettleDomestic(from);
            case "overseas" -> dailyPriceResettlementService.resettleOverseas(from);
            default -> dailyPriceResettlementService.resettleDomestic(from)
                + dailyPriceResettlementService.resettleOverseas(from);
        };
        log.info("[admin] 가격 재확정 일괄 복구 수동 트리거 완료: 처리종목수={}", processed);
        return ResponseEntity.ok("가격 재확정 복구 완료: 처리종목수=" + processed);
    }

    @PostMapping("/prices/repair-adjusted")
    @Operation(summary = "수정주가 소급 재조정(액면분할/병합) 복구",
        description = "from~오늘 구간에서 가격제한폭을 넘는 종가 점프를 스캔하고, dryRun=false면 걸린 종목만 전 구간 재백필한다. "
            + "dryRun=true(기본값)면 외부 API 호출 없이 스캔 결과만 반환하니 먼저 그걸로 확인할 것.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<PriceJumpReport>> repairAdjustedPrices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        log.info("[admin] 수정주가 재조정 복구 수동 트리거 시작: from={}, dryRun={}", from, dryRun);
        List<PriceJumpReport> jumps = dailyPriceIntegrityService.repairDomesticAdjusted(from, dryRun);
        log.info("[admin] 수정주가 재조정 복구 수동 트리거 완료: 발견={}건, dryRun={}", jumps.size(), dryRun);
        return ResponseEntity.ok(jumps);
    }

    @PostMapping("/prices/backfill-regular-close")
    @Operation(summary = "정규장 종가 소급 백필(1회성 마이그레이션)",
        description = "close_price를 정규장 종가로 전환(2026-09-21)하기 전에 저장된 최근 거래일 행들을 1분봉 API로 소급 확정한다. "
            + "stockCodes 생략 시 국내 전종목, days 생략 시 재확정 윈도우(20일). 이미 확정된 행은 건너뛰어 멱등이다. "
            + "전종목은 약 1.5시간이 걸리므로 관심종목 등 소규모로 먼저 검증할 것. 서버 기동 직후엔 캐치업이 새 행을 만들어 "
            + "미확정 행이 남을 수 있으니 캐치업이 끝난 뒤 한 번 더 호출할 것.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<RegularCloseBackfillResult> backfillRegularClose(
            @RequestParam(required = false) List<String> stockCodes,
            @RequestParam(required = false) Integer days) {
        log.info("[admin] 정규장 종가 백필 수동 트리거 시작: 종목수={}, days={}",
            stockCodes == null ? "전체" : stockCodes.size(), days);
        RegularCloseBackfillResult result = regularCloseBackfillService.backfill(stockCodes, days);
        log.info("[admin] 정규장 종가 백필 수동 트리거 완료: {}", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/scores/rebuild")
    @Operation(summary = "스코어 재구성(파괴적, 비동기 배치)",
        description = "from 이후 스코어를 전부 지우고 전 상장종목을 다시 계산하는 Spring Batch Job을 시작하고 executionId를 바로 돌려준다. "
            + "진행 상황은 GET /batch/executions/{id}로 본다. 계산이 중간에 실패하면 FAILED로 남고 "
            + "POST /batch/executions/{id}/restart로 삭제는 건너뛰고 끊긴 청크부터 이어서 계산한다. "
            + "지우는 작업이므로 confirm=true를 명시해야 실행된다. 이미 실행 중이면 409. "
            + "정규화는 하지 않으니 끝난 뒤 /refresh가 필요하다.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<String> rebuildScores(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "false") boolean confirm) {
        if (!confirm) {
            return ResponseEntity.badRequest()
                .body("from 이후 스코어를 전부 지우고 다시 계산합니다. 실행하려면 confirm=true를 지정하세요.");
        }
        log.info("[admin] 스코어 재구성 배치 시작 요청: from={}", from);
        try {
            long executionId = batchJobLaunchService.launchScoreRebuild(from);
            return ResponseEntity.accepted().body("스코어 재구성 시작: from=%s, executionId=%d".formatted(from, executionId));
        } catch (BatchLaunchRejectedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @GetMapping("/batch/executions")
    @Operation(summary = "최근 배치 실행 목록",
        description = "모든 Job의 최근 실행을 만든 시각 내림차순으로 돌려준다(Step 없는 요약, limit 1~50, 기본 20). "
            + "Step별 상세는 GET /batch/executions/{id}.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<BatchExecutionView>> getRecentBatchExecutions(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(batchJobLaunchService.recentExecutions(limit));
    }

    @GetMapping("/batch/executions/{executionId}")
    @Operation(summary = "배치 실행 상태 조회", description = "Job 상태와 Step별 읽기/쓰기/skip 수를 돌려준다.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<BatchExecutionView> getBatchExecution(@PathVariable long executionId) {
        return ResponseEntity.ok(batchJobLaunchService.getExecution(executionId));
    }

    @PostMapping("/batch/executions/{executionId}/restart")
    @Operation(summary = "실패한 배치 재시작",
        description = "FAILED/STOPPED 실행을 같은 파라미터로 다시 시작한다. 완료된 Step(삭제 단계 포함)은 건너뛰고 끊긴 청크부터 이어 간다.")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<String> restartBatchExecution(@PathVariable long executionId) {
        log.info("[admin] 배치 재시작 요청: executionId={}", executionId);
        try {
            long newExecutionId = batchJobLaunchService.restart(executionId);
            return ResponseEntity.accepted().body("배치 재시작: executionId=" + newExecutionId);
        } catch (BatchLaunchRejectedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
