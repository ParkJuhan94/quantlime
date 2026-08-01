package com.quantlime.market.service;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossInvestorTradingResponse;
import com.quantlime.infra.toss.dto.TossInvestorTradingResponse.InvestorTradingRecord;
import com.quantlime.market.domain.AggregationInterval;
import com.quantlime.market.domain.InvestorTrading;
import com.quantlime.market.domain.InvestorTradingAmounts;
import com.quantlime.market.repository.InvestorTradingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 코스피/코스닥 투자자별 매매대금(주간/월간)을 영속 저장한다. Toss
 * investor-trading 응답은 항상 최신순 최대 100건을 한 번의 호출로 주므로
 * (사용자 요청 범위인 주/월 단위 100건이면 각각 약 2년/8년치라 충분),
 * {@link com.quantlime.market.service.BenchmarkIndexBackfillService}처럼
 * "이미 충분히 쌓였으면 스킵" 방식이 아니라 매번 최신 100건을 그대로
 * 다시 조회해 신규는 추가하고 이미 있는 건 잠정치 갱신 여부만 확인한다 -
 * 그래야 당일/당주/당월처럼 장 종료 전까지 계속 갱신되는 잠정치를
 * 놓치지 않는다(sourceUpdatedAt 비교, DomesticDailyPriceService.upsertToday와
 * 같은 성격의 처리).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestorTradingBackfillService {

    private static final List<String> MARKET_CODES = List.of("KOSPI", "KOSDAQ");
    // Toss investor-trading count 파라미터 상한.
    private static final int PAGE_SIZE = 100;

    private final InvestorTradingRepository investorTradingRepository;
    private final TossApiClient tossApiClient;

    public void refreshAllIfNeeded() {
        for (String marketCode : MARKET_CODES) {
            for (AggregationInterval interval : AggregationInterval.values()) {
                refresh(marketCode, interval);
            }
        }
    }

    private void refresh(String marketCode, AggregationInterval interval) {
        TossInvestorTradingResponse response =
            tossApiClient.getInvestorTrading(marketCode, interval.getTossValue(), PAGE_SIZE, null);
        List<InvestorTradingRecord> records = response.result().records();
        if (records == null || records.isEmpty()) {
            log.debug("투자자별 매매대금 데이터 없음: marketCode={}, interval={}", marketCode, interval);
            return;
        }

        int savedCount = 0;
        int updatedCount = 0;
        for (InvestorTradingRecord record : records) {
            UpsertResult result = upsert(marketCode, interval, record);
            if (result == UpsertResult.SAVED) {
                savedCount++;
            } else if (result == UpsertResult.UPDATED) {
                updatedCount++;
            }
        }
        log.info("투자자별 매매대금 갱신 완료: marketCode={}, interval={}, 신규저장={}건, 잠정치갱신={}건",
            marketCode, interval, savedCount, updatedCount);
    }

    private UpsertResult upsert(String marketCode, AggregationInterval interval, InvestorTradingRecord record) {
        LocalDate baseDate = LocalDate.parse(record.date());
        LocalDateTime sourceUpdatedAt = OffsetDateTime.parse(record.updatedAt()).toLocalDateTime();
        InvestorTradingAmounts amounts = toAmounts(record);

        Optional<InvestorTrading> existing = investorTradingRepository
            .findByMarketCodeAndAggregationIntervalAndBaseDate(marketCode, interval, baseDate);
        if (existing.isEmpty()) {
            investorTradingRepository.save(
                InvestorTrading.of(marketCode, interval, baseDate, sourceUpdatedAt, amounts));
            return UpsertResult.SAVED;
        }

        InvestorTrading current = existing.get();
        if (current.getSourceUpdatedAt().isEqual(sourceUpdatedAt)) {
            return UpsertResult.SKIPPED;
        }
        current.updateAmounts(sourceUpdatedAt, amounts);
        investorTradingRepository.save(current);
        return UpsertResult.UPDATED;
    }

    private InvestorTradingAmounts toAmounts(InvestorTradingRecord record) {
        TossInvestorTradingResponse.InstitutionTradingAmount institution = record.institution();
        TossInvestorTradingResponse.InstitutionBreakdown breakdown = institution.breakdown();
        return InvestorTradingAmounts.builder()
            .individualBuyAmount(parse(record.individual().buyAmount()))
            .individualSellAmount(parse(record.individual().sellAmount()))
            .foreignerBuyAmount(parse(record.foreigner().buyAmount()))
            .foreignerSellAmount(parse(record.foreigner().sellAmount()))
            .institutionBuyAmount(parse(institution.buyAmount()))
            .institutionSellAmount(parse(institution.sellAmount()))
            .financialInvestmentBuyAmount(parse(breakdown.financialInvestment().buyAmount()))
            .financialInvestmentSellAmount(parse(breakdown.financialInvestment().sellAmount()))
            .insuranceBuyAmount(parse(breakdown.insurance().buyAmount()))
            .insuranceSellAmount(parse(breakdown.insurance().sellAmount()))
            .trustBuyAmount(parse(breakdown.trust().buyAmount()))
            .trustSellAmount(parse(breakdown.trust().sellAmount()))
            .privateEquityFundBuyAmount(parse(breakdown.privateEquityFund().buyAmount()))
            .privateEquityFundSellAmount(parse(breakdown.privateEquityFund().sellAmount()))
            .bankBuyAmount(parse(breakdown.bank().buyAmount()))
            .bankSellAmount(parse(breakdown.bank().sellAmount()))
            .otherFinancialInstitutionBuyAmount(parse(breakdown.otherFinancialInstitution().buyAmount()))
            .otherFinancialInstitutionSellAmount(parse(breakdown.otherFinancialInstitution().sellAmount()))
            .pensionFundBuyAmount(parse(breakdown.pensionFund().buyAmount()))
            .pensionFundSellAmount(parse(breakdown.pensionFund().sellAmount()))
            .otherCorporationBuyAmount(parse(record.otherCorporation().buyAmount()))
            .otherCorporationSellAmount(parse(record.otherCorporation().sellAmount()))
            .build();
    }

    private Long parse(String raw) {
        return Long.parseLong(raw);
    }

    private enum UpsertResult {
        SAVED, UPDATED, SKIPPED
    }
}
