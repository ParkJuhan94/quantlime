package com.quantlime.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossInvestorTradingResponse;
import com.quantlime.infra.toss.dto.TossInvestorTradingResponse.InstitutionBreakdown;
import com.quantlime.infra.toss.dto.TossInvestorTradingResponse.InstitutionTradingAmount;
import com.quantlime.infra.toss.dto.TossInvestorTradingResponse.InvestorTradingAmount;
import com.quantlime.infra.toss.dto.TossInvestorTradingResponse.InvestorTradingRecord;
import com.quantlime.market.domain.AggregationInterval;
import com.quantlime.market.domain.InvestorTrading;
import com.quantlime.market.domain.InvestorTradingAmounts;
import com.quantlime.market.repository.InvestorTradingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
class InvestorTradingBackfillServiceTest {

    @Mock
    private InvestorTradingRepository investorTradingRepository;

    @Mock
    private TossApiClient tossApiClient;

    @InjectMocks
    private InvestorTradingBackfillService investorTradingBackfillService;

    @Test
    @DisplayName("[신규 기록은 저장하고, 시장 2개 x 주간/월간 총 4회 조회한다]")
    void refreshAllIfNeeded_newRecords_savesAll() {
        // given
        given(tossApiClient.getInvestorTrading(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eq(100), any()))
            .willReturn(responseWith(record("2026-07-29", "2026-07-29T20:00:02+09:00")));
        given(investorTradingRepository.findByMarketCodeAndAggregationIntervalAndBaseDate(any(), any(), any()))
            .willReturn(Optional.empty());

        // when
        investorTradingBackfillService.refreshAllIfNeeded();

        // then: KOSPI/KOSDAQ x WEEKLY/MONTHLY = 4회 API 호출, 4건 저장
        verify(tossApiClient, times(4)).getInvestorTrading(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eq(100), any());
        verify(investorTradingRepository, times(4)).save(any(InvestorTrading.class));
    }

    @Test
    @DisplayName("[이미 저장된 기록이고 sourceUpdatedAt이 같으면 덮어쓰지 않는다]")
    void refreshAllIfNeeded_unchangedRecord_skipsUpdate() {
        // given
        String updatedAt = "2026-07-24T20:01:32+09:00";
        given(tossApiClient.getInvestorTrading(eq("KOSPI"), eq("1w"), eq(100), any()))
            .willReturn(responseWith(record("2026-07-24", updatedAt)));
        given(tossApiClient.getInvestorTrading(eq("KOSPI"), eq("1mo"), eq(100), any()))
            .willReturn(responseWith(record("2026-07-24", updatedAt)));
        given(tossApiClient.getInvestorTrading(eq("KOSDAQ"), any(), eq(100), any()))
            .willReturn(responseWith(record("2026-07-24", updatedAt)));

        InvestorTrading existing = existingWithSourceUpdatedAt(updatedAt);
        given(investorTradingRepository.findByMarketCodeAndAggregationIntervalAndBaseDate(any(), any(), any()))
            .willReturn(Optional.of(existing));

        // when
        investorTradingBackfillService.refreshAllIfNeeded();

        // then: 이미 있고 updatedAt도 같으니 저장/갱신 없음
        verify(investorTradingRepository, never()).save(any());
    }

    @Test
    @DisplayName("[당일 잠정치처럼 sourceUpdatedAt이 바뀌면 기존 행을 덮어쓴다]")
    void refreshAllIfNeeded_changedSourceUpdatedAt_overwritesExisting() {
        // given
        given(tossApiClient.getInvestorTrading(eq("KOSPI"), eq("1w"), eq(100), any()))
            .willReturn(responseWith(record("2026-07-29", "2026-07-29T20:00:02+09:00")));
        given(tossApiClient.getInvestorTrading(eq("KOSPI"), eq("1mo"), eq(100), any()))
            .willReturn(responseWith(List.of()));
        given(tossApiClient.getInvestorTrading(eq("KOSDAQ"), any(), eq(100), any()))
            .willReturn(responseWith(List.of()));

        InvestorTrading existing = existingWithSourceUpdatedAt("2026-07-29T15:00:00+09:00");
        given(investorTradingRepository.findByMarketCodeAndAggregationIntervalAndBaseDate(
            eq("KOSPI"), eq(AggregationInterval.WEEKLY), any()))
            .willReturn(Optional.of(existing));

        // when
        investorTradingBackfillService.refreshAllIfNeeded();

        // then: 신규 저장 없이 기존 행 하나만 덮어써진다(save가 갱신된 엔티티로 1회 호출)
        ArgumentCaptor<InvestorTrading> captor = ArgumentCaptor.forClass(InvestorTrading.class);
        verify(investorTradingRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getSourceUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 29, 20, 0, 2));
    }

    private InvestorTrading existingWithSourceUpdatedAt(String updatedAt) {
        return InvestorTrading.of(
            "KOSPI", AggregationInterval.WEEKLY, LocalDate.of(2026, 7, 24),
            java.time.OffsetDateTime.parse(updatedAt).toLocalDateTime(),
            defaultAmounts());
    }

    private InvestorTradingAmounts defaultAmounts() {
        return InvestorTradingAmounts.builder()
            .individualBuyAmount(1L).individualSellAmount(1L)
            .foreignerBuyAmount(1L).foreignerSellAmount(1L)
            .institutionBuyAmount(1L).institutionSellAmount(1L)
            .financialInvestmentBuyAmount(1L).financialInvestmentSellAmount(1L)
            .insuranceBuyAmount(1L).insuranceSellAmount(1L)
            .trustBuyAmount(1L).trustSellAmount(1L)
            .privateEquityFundBuyAmount(1L).privateEquityFundSellAmount(1L)
            .bankBuyAmount(1L).bankSellAmount(1L)
            .otherFinancialInstitutionBuyAmount(1L).otherFinancialInstitutionSellAmount(1L)
            .pensionFundBuyAmount(1L).pensionFundSellAmount(1L)
            .otherCorporationBuyAmount(1L).otherCorporationSellAmount(1L)
            .build();
    }

    private InvestorTradingRecord record(String date, String updatedAt) {
        InvestorTradingAmount amount = new InvestorTradingAmount("100", "90");
        InstitutionBreakdown breakdown = new InstitutionBreakdown(
            amount, amount, amount, amount, amount, amount, amount);
        InstitutionTradingAmount institution = new InstitutionTradingAmount("100", "90", breakdown);
        return new InvestorTradingRecord(date, updatedAt, amount, amount, institution, amount);
    }

    private TossInvestorTradingResponse responseWith(InvestorTradingRecord record) {
        return responseWith(List.of(record));
    }

    private TossInvestorTradingResponse responseWith(List<InvestorTradingRecord> records) {
        return new TossInvestorTradingResponse(
            new TossInvestorTradingResponse.InvestorTradingResult(null, records));
    }
}
