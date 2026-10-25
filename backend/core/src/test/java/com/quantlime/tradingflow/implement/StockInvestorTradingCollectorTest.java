package com.quantlime.tradingflow.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.ForeignerHolding;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.InstitutionBreakdown;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.InstitutionTradingVolume;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.InvestorTradingVolume;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.StockInvestorTradingRecord;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.StockInvestorTradingResult;
import com.quantlime.tradingflow.domain.InvestorVolume;
import com.quantlime.tradingflow.domain.StockInvestorTrading;
import com.quantlime.tradingflow.implement.StockInvestorTradingCollector.UpsertSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StockInvestorTradingCollectorTest {

    private static final String STOCK = "005930";

    @Mock
    private StockInvestorTradingReader reader;

    @Mock
    private StockInvestorTradingAppender appender;

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private StockInvestorTradingCollector collector;

    private InvestorTradingVolume volume(String buy, String sell, String net) {
        return new InvestorTradingVolume(buy, sell, net);
    }

    /** 기관 세부 7개 - 순매수가 1~7이 되게 해서 필드 매핑이 뒤바뀌지 않는지 검증한다. */
    private InstitutionBreakdown breakdown() {
        return new InstitutionBreakdown(volume("1", "0", "1"), volume("2", "0", "2"), volume("3", "0", "3"),
            volume("4", "0", "4"), volume("5", "0", "5"), volume("6", "0", "6"), volume("7", "0", "7"));
    }

    /** 확정 기록: 개인·기타법인·기관 세부·외국인 보유가 모두 채워진다. */
    private StockInvestorTradingRecord confirmed(String date, String updatedAt) {
        return new StockInvestorTradingRecord(date, updatedAt, volume("100", "300", "-200"),
            volume("500", "400", "100"), new InstitutionTradingVolume("300", "200", "100", breakdown()),
            volume("10", "10", "0"), new ForeignerHolding("1000000", "5000000", "0.5089"));
    }

    /** 장중 잠정 기록: 개인·기타법인·기관 세부·외국인 보유가 null이다. */
    private StockInvestorTradingRecord provisional(String date, String updatedAt) {
        return new StockInvestorTradingRecord(date, updatedAt, null,
            volume("500", "400", "100"), new InstitutionTradingVolume("300", "200", "100", null), null, null);
    }

    private com.quantlime.tradingflow.domain.InstitutionBreakdown existingBreakdown() {
        return com.quantlime.tradingflow.domain.InstitutionBreakdown.builder().financialInvestmentNetBuyVolume(1L)
            .insuranceNetBuyVolume(2L).trustNetBuyVolume(3L).privateEquityFundNetBuyVolume(4L).bankNetBuyVolume(5L)
            .otherFinancialInstitutionNetBuyVolume(6L).pensionFundNetBuyVolume(7L).build();
    }

    private TossStockInvestorTradingResponse response(String nextUntil, StockInvestorTradingRecord... records) {
        return new TossStockInvestorTradingResponse(new StockInvestorTradingResult(nextUntil, List.of(records)));
    }

    @Test
    @DisplayName("[신규 기록은 저장하고 확정치는 개인·기타법인·외국인 보유까지 채운다]")
    void refreshRecent_newConfirmedRecord_saved() {
        given(tossApiClient.getStockInvestorTrading(STOCK, 20, null))
            .willReturn(response(null, confirmed("2026-10-23", "2026-10-23T18:05:00+09:00")));

        UpsertSummary summary = collector.refreshRecent(STOCK, 20);

        ArgumentCaptor<StockInvestorTrading> saved = ArgumentCaptor.forClass(StockInvestorTrading.class);
        verify(appender).save(saved.capture());
        StockInvestorTrading row = saved.getValue();
        assertThat(summary).isEqualTo(new UpsertSummary(1, 0, 0));
        assertThat(row.getStockCode()).isEqualTo(STOCK);
        assertThat(row.getTradeDate()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(row.getIndividual().getNetBuyVolume()).isEqualTo(-200L);
        assertThat(row.getForeigner().getNetBuyVolume()).isEqualTo(100L);
        assertThat(row.getForeignerHoldingRate()).isEqualTo(0.5089);
        assertThat(row.isConfirmed()).isTrue();
    }

    @Test
    @DisplayName("[장중 잠정 기록은 null 분류를 0으로 채우지 않고 null 그대로 저장한다]")
    void refreshRecent_provisionalRecord_keepsNullGroups() {
        given(tossApiClient.getStockInvestorTrading(STOCK, 20, null))
            .willReturn(response(null, provisional("2026-10-23", "2026-10-23T14:35:00+09:00")));

        collector.refreshRecent(STOCK, 20);

        ArgumentCaptor<StockInvestorTrading> saved = ArgumentCaptor.forClass(StockInvestorTrading.class);
        verify(appender).save(saved.capture());
        StockInvestorTrading row = saved.getValue();
        assertThat(row.getIndividual()).isNull();
        assertThat(row.getOtherCorporation()).isNull();
        assertThat(row.getForeignerHoldingRate()).isNull();
        assertThat(row.getForeigner().getNetBuyVolume()).isEqualTo(100L);
        assertThat(row.isConfirmed()).isFalse();
    }

    @Test
    @DisplayName("[같은 updatedAt이면 건너뛰고 바뀌었으면(잠정→확정) 기존 행을 덮어쓴다]")
    void refreshRecent_existingRow_skipsSameAndOverwritesChanged() {
        StockInvestorTrading sameStamp = StockInvestorTrading.of(STOCK, LocalDate.of(2026, 10, 22),
            LocalDateTime.of(2026, 10, 22, 18, 0), InvestorVolume.of(1, 1, 0), InvestorVolume.of(1, 1, 0),
            InvestorVolume.of(1, 1, 0), existingBreakdown(), null, null, null);
        StockInvestorTrading provisionalRow = StockInvestorTrading.of(STOCK, LocalDate.of(2026, 10, 23),
            LocalDateTime.of(2026, 10, 23, 14, 35), null, InvestorVolume.of(500, 400, 100),
            InvestorVolume.of(300, 200, 100), null, null, null, null);
        given(reader.findBetween(eq(STOCK), any(), any())).willReturn(List.of(sameStamp, provisionalRow));
        given(tossApiClient.getStockInvestorTrading(STOCK, 20, null)).willReturn(response(null,
            confirmed("2026-10-23", "2026-10-23T18:05:00+09:00"),
            confirmed("2026-10-22", "2026-10-22T18:00:00+09:00")));

        UpsertSummary summary = collector.refreshRecent(STOCK, 20);

        assertThat(summary).isEqualTo(new UpsertSummary(0, 1, 1));
        assertThat(provisionalRow.isConfirmed()).isTrue();
        assertThat(provisionalRow.getIndividual().getNetBuyVolume()).isEqualTo(-200L);
        verify(appender, times(1)).save(provisionalRow);
        verify(appender, never()).save(sameStamp);
    }

    @Test
    @DisplayName("[기관 세부 7개 분류의 순매수를 각자 맞는 컬럼으로 저장한다]")
    void refreshRecent_confirmedRecord_mapsInstitutionBreakdownByName() {
        given(tossApiClient.getStockInvestorTrading(STOCK, 20, null))
            .willReturn(response(null, confirmed("2026-10-23", "2026-10-23T18:05:00+09:00")));

        collector.refreshRecent(STOCK, 20);

        ArgumentCaptor<StockInvestorTrading> saved = ArgumentCaptor.forClass(StockInvestorTrading.class);
        verify(appender).save(saved.capture());
        var breakdown = saved.getValue().getInstitutionBreakdown();
        assertThat(breakdown.getFinancialInvestmentNetBuyVolume()).isEqualTo(1L);
        assertThat(breakdown.getInsuranceNetBuyVolume()).isEqualTo(2L);
        assertThat(breakdown.getTrustNetBuyVolume()).isEqualTo(3L);
        assertThat(breakdown.getPrivateEquityFundNetBuyVolume()).isEqualTo(4L);
        assertThat(breakdown.getBankNetBuyVolume()).isEqualTo(5L);
        assertThat(breakdown.getOtherFinancialInstitutionNetBuyVolume()).isEqualTo(6L);
        assertThat(breakdown.getPensionFundNetBuyVolume()).isEqualTo(7L);
    }

    @Test
    @DisplayName("[잠정 기록의 기관 세부는 null로 저장한다]")
    void refreshRecent_provisionalRecord_leavesInstitutionBreakdownNull() {
        given(tossApiClient.getStockInvestorTrading(STOCK, 20, null))
            .willReturn(response(null, provisional("2026-10-23", "2026-10-23T14:35:00+09:00")));

        collector.refreshRecent(STOCK, 20);

        ArgumentCaptor<StockInvestorTrading> saved = ArgumentCaptor.forClass(StockInvestorTrading.class);
        verify(appender).save(saved.capture());
        assertThat(saved.getValue().getInstitutionBreakdown()).isNull();
    }

    @Test
    @DisplayName("[세부 컬럼 추가 전에 저장된 행은 updatedAt이 같아도 세부를 채워 덮어쓴다 - 이력 재백필]")
    void refreshRecent_existingRowWithoutBreakdown_isFilledEvenWithSameStamp() {
        StockInvestorTrading oldRow = StockInvestorTrading.of(STOCK, LocalDate.of(2026, 10, 22),
            LocalDateTime.of(2026, 10, 22, 18, 0), InvestorVolume.of(100, 300, -200),
            InvestorVolume.of(500, 400, 100), InvestorVolume.of(300, 200, 100), null,
            InvestorVolume.of(10, 10, 0), 1_000_000L, 0.5089);
        given(reader.findBetween(eq(STOCK), any(), any())).willReturn(List.of(oldRow));
        given(tossApiClient.getStockInvestorTrading(STOCK, 20, null))
            .willReturn(response(null, confirmed("2026-10-22", "2026-10-22T18:00:00+09:00")));

        UpsertSummary summary = collector.refreshRecent(STOCK, 20);

        assertThat(summary).isEqualTo(new UpsertSummary(0, 1, 0));
        assertThat(oldRow.lacksInstitutionBreakdown()).isFalse();
        assertThat(oldRow.getInstitutionBreakdown().getPensionFundNetBuyVolume()).isEqualTo(7L);
        verify(appender).save(oldRow);
    }

    @Test
    @DisplayName("[세부가 없는 옛 행이라도 응답에도 세부가 없으면(잠정) 같은 updatedAt에서 건너뛴다]")
    void refreshRecent_existingRowWithoutBreakdown_skippedWhenResponseHasNone() {
        StockInvestorTrading provisionalRow = StockInvestorTrading.of(STOCK, LocalDate.of(2026, 10, 23),
            LocalDateTime.of(2026, 10, 23, 14, 35), null, InvestorVolume.of(500, 400, 100),
            InvestorVolume.of(300, 200, 100), null, null, null, null);
        given(reader.findBetween(eq(STOCK), any(), any())).willReturn(List.of(provisionalRow));
        given(tossApiClient.getStockInvestorTrading(STOCK, 20, null))
            .willReturn(response(null, provisional("2026-10-23", "2026-10-23T14:35:00+09:00")));

        UpsertSummary summary = collector.refreshRecent(STOCK, 20);

        assertThat(summary).isEqualTo(new UpsertSummary(0, 0, 1));
        verify(appender, never()).save(provisionalRow);
    }

    @Test
    @DisplayName("[외국인·기관이 빠진 비정상 레코드는 건너뛰고 나머지는 저장한다]")
    void refreshRecent_malformedRecord_skipped() {
        StockInvestorTradingRecord broken = new StockInvestorTradingRecord("2026-10-23",
            "2026-10-23T18:05:00+09:00", null, null, null, null, null);
        given(tossApiClient.getStockInvestorTrading(STOCK, 20, null)).willReturn(response(null, broken,
            confirmed("2026-10-22", "2026-10-22T18:00:00+09:00")));

        UpsertSummary summary = collector.refreshRecent(STOCK, 20);

        assertThat(summary).isEqualTo(new UpsertSummary(1, 0, 0));
    }

    @Test
    @DisplayName("[백필은 nextUntil 커서로 100건씩 이어 받고 days에 닿으면 멈춘다]")
    void backfill_pagesByCursorUntilDays() {
        given(tossApiClient.getStockInvestorTrading(STOCK, 100, null))
            .willReturn(response("2026-07-01", confirmed("2026-10-23", "2026-10-23T18:05:00+09:00")));
        given(tossApiClient.getStockInvestorTrading(STOCK, 100, "2026-07-01"))
            .willReturn(response("2026-04-01", confirmed("2026-07-01", "2026-07-01T18:05:00+09:00")));
        given(tossApiClient.getStockInvestorTrading(STOCK, 100, "2026-04-01"))
            .willReturn(response(null, confirmed("2026-04-01", "2026-04-01T18:05:00+09:00")));

        UpsertSummary summary = collector.backfill(STOCK, 300);

        // 한 페이지에 1건씩만 내려와도 days(300) 전에 끝(nextUntil null)을 만나면 멈춘다
        assertThat(summary.saved()).isEqualTo(3);
        verify(tossApiClient, times(3)).getStockInvestorTrading(eq(STOCK), anyInt(), any());
    }

    @Test
    @DisplayName("[백필은 남은 건수가 100보다 작으면 그만큼만 요청한다]")
    void backfill_requestsOnlyRemaining() {
        given(tossApiClient.getStockInvestorTrading(STOCK, 30, null)).willReturn(response("2026-09-01",
            confirmed("2026-10-23", "2026-10-23T18:05:00+09:00")));

        collector.backfill(STOCK, 30);

        verify(tossApiClient).getStockInvestorTrading(STOCK, 30, null);
    }

    @Test
    @DisplayName("[백필 응답이 비어 있으면 더 이어 받지 않고 끝낸다]")
    void backfill_emptyPage_stops() {
        given(tossApiClient.getStockInvestorTrading(STOCK, 100, null))
            .willReturn(new TossStockInvestorTradingResponse(new StockInvestorTradingResult(null, List.of())));

        UpsertSummary summary = collector.backfill(STOCK, 400);

        assertThat(summary).isEqualTo(new UpsertSummary(0, 0, 0));
        verify(tossApiClient, times(1)).getStockInvestorTrading(eq(STOCK), anyInt(), any());
    }
}
