package com.quantlime.common.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.batch.common.BatchExecutionView;
import com.quantlime.batch.common.BatchJobLaunchService;
import com.quantlime.batch.common.BatchLaunchRejectedException;
import com.quantlime.common.exception.GlobalExceptionHandler;
import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.price.service.DailyPriceIntegrityService;
import com.quantlime.price.service.DailyPriceResettlementService;
import com.quantlime.price.service.RegularCloseBackfillService;
import com.quantlime.stock.service.DomesticStockMasterSyncService;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import com.quantlime.tradingflow.service.StockInvestorTradingRefreshService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 운영 데이터 복구 트리거의 라우팅·파라미터 전달·파괴적 작업의 확인 장치를 검증한다. 전체 컨텍스트를 띄우지 않는
 * standalone 방식이라 컨텍스트 캐시에 영향이 없고(String 응답의 한글이 깨지지 않게 UTF-8 컨버터를 지정한다), 권한(ROLE_ADMIN)은 SecurityConfig 몫이라 다루지 않는다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DataOpsAdminControllerTest {

    @Mock
    private DomesticStockMasterSyncService domesticStockMasterSyncService;

    @Mock
    private OverseasStockMasterSyncService overseasStockMasterSyncService;

    @Mock
    private MarketDataRefreshService marketDataRefreshService;

    @Mock
    private DailyPriceResettlementService dailyPriceResettlementService;

    @Mock
    private DailyPriceIntegrityService dailyPriceIntegrityService;

    @Mock
    private RegularCloseBackfillService regularCloseBackfillService;

    @Mock
    private BatchJobLaunchService batchJobLaunchService;

    @Mock
    private StockInvestorTradingRefreshService stockInvestorTradingRefreshService;

    @InjectMocks
    private DataOpsAdminController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    @DisplayName("[종목별 매매동향 백필은 기본 400건으로 시작하고 202를 돌려준다]")
    void backfillStockInvestorTrading_defaultDays_starts() throws Exception {
        given(stockInvestorTradingRefreshService.startBackfillAsync(400)).willReturn(true);

        mockMvc.perform(post("/api/admin/data/stock-investor-trading/backfill"))
            .andExpect(status().isAccepted());

        verify(stockInvestorTradingRefreshService).startBackfillAsync(400);
    }

    @Test
    @DisplayName("[종목별 매매동향 백필은 이미 실행 중이면 409다]")
    void backfillStockInvestorTrading_alreadyRunning_returns409() throws Exception {
        given(stockInvestorTradingRefreshService.startBackfillAsync(100)).willReturn(false);

        mockMvc.perform(post("/api/admin/data/stock-investor-trading/backfill").param("days", "100"))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("[종목별 매매동향 백필은 days가 범위를 벗어나면 400이고 시작하지 않는다]")
    void backfillStockInvestorTrading_invalidDays_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/data/stock-investor-trading/backfill").param("days", "0"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/data/stock-investor-trading/backfill").param("days", "1001"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(stockInvestorTradingRefreshService);
    }

    @Test
    @DisplayName("[종목별 매매동향 최근 구간 갱신은 시작되면 202, 이미 실행 중이면 409다]")
    void refreshStockInvestorTrading_startsOrConflicts() throws Exception {
        given(stockInvestorTradingRefreshService.startRefreshAsync()).willReturn(true, false);

        mockMvc.perform(post("/api/admin/data/stock-investor-trading/refresh")).andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/data/stock-investor-trading/refresh")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("[스코어 재구성은 confirm 없이 호출하면 400이고 배치를 시작하지 않는다]")
    void rebuildScores_withoutConfirm_isRejected() throws Exception {
        mockMvc.perform(post("/api/admin/data/scores/rebuild").param("from", "2026-10-01"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(batchJobLaunchService);
    }

    @Test
    @DisplayName("[스코어 재구성은 confirm=true일 때만 from을 넘겨 배치를 시작하고 202로 executionId를 돌려준다]")
    void rebuildScores_withConfirm_launchesBatch() throws Exception {
        given(batchJobLaunchService.launchScoreRebuild(LocalDate.of(2026, 10, 1))).willReturn(7L);

        mockMvc.perform(post("/api/admin/data/scores/rebuild").param("from", "2026-10-01").param("confirm", "true"))
            .andExpect(status().isAccepted());

        verify(batchJobLaunchService).launchScoreRebuild(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("[스코어 재구성은 이미 실행 중이거나 메타 테이블이 없어 거부되면 409다]")
    void rebuildScores_rejected_isConflict() throws Exception {
        given(batchJobLaunchService.launchScoreRebuild(LocalDate.of(2026, 10, 1)))
            .willThrow(new BatchLaunchRejectedException("이미 실행 중인 배치가 있습니다"));

        mockMvc.perform(post("/api/admin/data/scores/rebuild").param("from", "2026-10-01").param("confirm", "true"))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("[배치 실행 상태 조회는 서비스가 만든 뷰를 그대로 돌려준다]")
    void getBatchExecution_returnsView() throws Exception {
        given(batchJobLaunchService.getExecution(7L)).willReturn(
            new BatchExecutionView(7L, 70L, "scoreRebuildJob", "FAILED", "FAILED", "", null, null, null, List.of()));

        mockMvc.perform(get("/api/admin/data/batch/executions/7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(7))
            .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("[최근 배치 실행 목록은 limit 기본값 20을 서비스에 넘기고, 지정하면 그대로 넘긴다]")
    void getRecentBatchExecutions_passesLimit() throws Exception {
        given(batchJobLaunchService.recentExecutions(20)).willReturn(List.of(
            new BatchExecutionView(9L, 90L, "backtestWeeklyJob", "COMPLETED", "COMPLETED", "", null, null, null, List.of())));
        given(batchJobLaunchService.recentExecutions(5)).willReturn(List.of());

        mockMvc.perform(get("/api/admin/data/batch/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].executionId").value(9))
            .andExpect(jsonPath("$[0].jobName").value("backtestWeeklyJob"));
        mockMvc.perform(get("/api/admin/data/batch/executions").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("[배치 재시작은 새 executionId로 202, 재시작할 수 없는 상태면 409다]")
    void restartBatchExecution_acceptedOrConflict() throws Exception {
        given(batchJobLaunchService.restart(7L)).willReturn(8L);
        given(batchJobLaunchService.restart(9L)).willThrow(new BatchLaunchRejectedException("재시작할 수 없는 상태입니다"));

        mockMvc.perform(post("/api/admin/data/batch/executions/7/restart")).andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/data/batch/executions/9/restart")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("[수정주가 복구는 dryRun 기본값이 true다]")
    void repairAdjusted_defaultsToDryRun() throws Exception {
        given(dailyPriceIntegrityService.repairDomesticAdjusted(LocalDate.of(2026, 10, 1), true)).willReturn(List.of());

        mockMvc.perform(post("/api/admin/data/prices/repair-adjusted").param("from", "2026-10-01"))
            .andExpect(status().isOk());

        verify(dailyPriceIntegrityService).repairDomesticAdjusted(LocalDate.of(2026, 10, 1), true);
    }

    @Test
    @DisplayName("[가격 재확정은 market에 맞는 쪽만 실행하고, 그 외 값은 국내+해외 둘 다 실행한다]")
    void resettle_routesByMarket() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        given(dailyPriceResettlementService.resettleDomestic(from)).willReturn(3);
        given(dailyPriceResettlementService.resettleOverseas(from)).willReturn(2);

        assertThat(postForBody("/api/admin/data/prices/resettle", "market", "domestic")).isEqualTo("가격 재확정 복구 완료: 처리종목수=3");
        assertThat(postForBody("/api/admin/data/prices/resettle", "market", "all")).isEqualTo("가격 재확정 복구 완료: 처리종목수=5");
    }

    @Test
    @DisplayName("[갭필은 종목코드가 있으면 단건만, 이미 실행 중이면 스킵 문구를 응답한다]")
    void refresh_singleAndSkip() throws Exception {
        mockMvc.perform(post("/api/admin/data/refresh").param("stockCode", "005930"))
            .andExpect(status().isOk());
        verify(marketDataRefreshService).refreshStock("005930");

        given(marketDataRefreshService.refreshAllExclusively()).willReturn(Optional.empty());
        assertThat(mockMvc.perform(post("/api/admin/data/refresh")).andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8)).isEqualTo("이미 다른 실행이 갱신 중입니다 - 스킵");
    }

    private String postForBody(String url, String name, String value) throws Exception {
        return mockMvc.perform(post(url).param(name, value).param("from", "2026-10-01")).andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    }
}
