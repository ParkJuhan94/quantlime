package com.quantlime.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.market.domain.AggregationInterval;
import com.quantlime.market.domain.InvestorTrading;
import com.quantlime.market.domain.InvestorTradingAmounts;
import com.quantlime.market.dto.response.InvestorTradingResponse;
import com.quantlime.market.repository.InvestorTradingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class InvestorTradingServiceTest {

    @Mock
    private InvestorTradingRepository investorTradingRepository;

    @InjectMocks
    private InvestorTradingService service;

    private InvestorTrading row(LocalDate baseDate, long individualBuy, long individualSell) {
        InvestorTradingAmounts amounts = InvestorTradingAmounts.builder()
            .individualBuyAmount(individualBuy).individualSellAmount(individualSell)
            .foreignerBuyAmount(5L).foreignerSellAmount(2L)
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
        return InvestorTrading.of("KOSPI", AggregationInterval.WEEKLY, baseDate,
            LocalDateTime.of(2026, 9, 30, 12, 0), amounts);
    }

    @Test
    @DisplayName("[저장소가 최신순으로 준 결과를 과거→최신 오름차순으로 뒤집어 순매수(매수-매도)로 변환한다]")
    void getInvestorTrading_reversesToAscending_andComputesNetBuy() {
        // given: 저장소는 baseDate 내림차순
        given(investorTradingRepository.findByMarketCodeAndAggregationIntervalOrderByBaseDateDesc(
            eq("KOSPI"), eq(AggregationInterval.WEEKLY), eq(PageRequest.of(0, 2))))
            .willReturn(List.of(row(LocalDate.of(2026, 9, 28), 100, 30), row(LocalDate.of(2026, 9, 21), 10, 40)));

        // when
        List<InvestorTradingResponse> result = service.getInvestorTrading("KOSPI", "weekly", 2);

        // then
        assertThat(result).extracting(InvestorTradingResponse::baseDate)
            .containsExactly(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28));
        assertThat(result.get(0).individualNetBuyAmount()).isEqualTo(-30L);
        assertThat(result.get(1).individualNetBuyAmount()).isEqualTo(70L);
        assertThat(result.get(1).foreignerNetBuyAmount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("[데이터가 없으면 빈 리스트]")
    void getInvestorTrading_noRows_returnsEmpty() {
        given(investorTradingRepository.findByMarketCodeAndAggregationIntervalOrderByBaseDateDesc(
            eq("KOSDAQ"), eq(AggregationInterval.MONTHLY), eq(PageRequest.of(0, 5))))
            .willReturn(List.of());

        assertThat(service.getInvestorTrading("KOSDAQ", "monthly", 5)).isEmpty();
    }

    @Test
    @DisplayName("[지원하지 않는 interval이면 저장소를 조회하기 전에 ValidationException]")
    void getInvestorTrading_invalidInterval_throws() {
        assertThatThrownBy(() -> service.getInvestorTrading("KOSPI", "daily", 5))
            .isInstanceOf(ValidationException.class);
    }
}
