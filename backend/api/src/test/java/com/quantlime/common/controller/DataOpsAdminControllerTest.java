package com.quantlime.common.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.common.exception.GlobalExceptionHandler;
import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.price.service.DailyPriceIntegrityService;
import com.quantlime.price.service.DailyPriceResettlementService;
import com.quantlime.price.service.RegularCloseBackfillService;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.service.DomesticStockMasterSyncService;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
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
    private ScoreService scoreService;

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
    @DisplayName("[스코어 재구성은 confirm 없이 호출하면 400이고 아무것도 지우지 않는다]")
    void rebuildScores_withoutConfirm_isRejected() throws Exception {
        mockMvc.perform(post("/api/admin/data/scores/rebuild").param("from", "2026-10-01"))
            .andExpect(status().isBadRequest());

        verify(scoreService, never()).rebuildScoresFrom(any());
    }

    @Test
    @DisplayName("[스코어 재구성은 confirm=true일 때만 from을 넘겨 실행한다]")
    void rebuildScores_withConfirm_runs() throws Exception {
        mockMvc.perform(post("/api/admin/data/scores/rebuild").param("from", "2026-10-01").param("confirm", "true"))
            .andExpect(status().isOk());

        verify(scoreService).rebuildScoresFrom(LocalDate.of(2026, 10, 1));
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
