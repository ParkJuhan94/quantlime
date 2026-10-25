package com.quantlime.tradingflow.implement;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.ForeignerHolding;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.InstitutionTradingVolume;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.InvestorTradingVolume;
import com.quantlime.infra.toss.dto.TossStockInvestorTradingResponse.StockInvestorTradingRecord;
import com.quantlime.tradingflow.domain.InstitutionBreakdown;
import com.quantlime.tradingflow.domain.InvestorVolume;
import com.quantlime.tradingflow.domain.StockInvestorTrading;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 토스 종목별 투자자 매매동향을 가져와 종목×거래일 단위로 영속 저장하는 구현 레이어. 응답은 항상 최신순이고
 * 호출당 최대 {@value #PAGE_SIZE}건이다. 지수 단위 {@code InvestorTradingCollector}와 같이 "이미 있으면 스킵"이
 * 아니라 매번 최근 구간을 다시 받아 신규는 추가하고, {@code sourceUpdatedAt}이 바뀐 행(잠정→확정, 외국인 보유 갱신)은
 * 덮어쓴다. 저장은 행 단위로 커밋한다(트랜잭션 없음 - {@code DomesticDailyPriceCollector}와 동일).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockInvestorTradingCollector {

    // 토스 investor-trading count 파라미터 상한.
    static final int PAGE_SIZE = 100;

    private final StockInvestorTradingReader stockInvestorTradingReader;
    private final StockInvestorTradingAppender stockInvestorTradingAppender;
    private final TossApiClient tossApiClient;

    /** 종목의 최근 {@code count}건을 한 번 호출로 받아 신규는 저장하고 바뀐 행은 덮어쓴다. */
    public UpsertSummary refreshRecent(String stockCode, int count) {
        TossStockInvestorTradingResponse response = tossApiClient.getStockInvestorTrading(stockCode, count, null);
        return upsertAll(stockCode, recordsOf(response));
    }

    /**
     * 종목의 과거 이력을 {@code nextUntil} 커서로 100건씩 {@code days}건까지 받아 저장한다. {@code days}는 달력
     * 일수가 아니라 거래일 기록 수다(400이면 약 19개월).
     */
    public UpsertSummary backfill(String stockCode, int days) {
        UpsertSummary total = UpsertSummary.EMPTY;
        int remaining = days;
        String until = null;
        while (remaining > 0) {
            TossStockInvestorTradingResponse response =
                tossApiClient.getStockInvestorTrading(stockCode, Math.min(PAGE_SIZE, remaining), until);
            List<StockInvestorTradingRecord> records = recordsOf(response);
            if (records.isEmpty()) {
                break;
            }
            total = total.plus(upsertAll(stockCode, records));
            remaining -= records.size();
            until = response.result().nextUntil();
            if (until == null || until.isBlank()) {
                break;
            }
        }
        return total;
    }

    private List<StockInvestorTradingRecord> recordsOf(@Nullable TossStockInvestorTradingResponse response) {
        if (response == null || response.result() == null || response.result().records() == null) {
            return List.of();
        }
        return response.result().records();
    }

    private UpsertSummary upsertAll(String stockCode, List<StockInvestorTradingRecord> records) {
        if (records.isEmpty()) {
            return UpsertSummary.EMPTY;
        }
        // 종목마다 기록 수만큼 단건 조회하지 않도록 이번 응답이 덮는 날짜 범위의 기존 행을 한 번에 읽는다.
        LocalDate oldest = records.stream().map(r -> LocalDate.parse(r.date())).min(LocalDate::compareTo).orElseThrow();
        LocalDate newest = records.stream().map(r -> LocalDate.parse(r.date())).max(LocalDate::compareTo).orElseThrow();
        Map<LocalDate, StockInvestorTrading> existingByDate = new HashMap<>();
        stockInvestorTradingReader.findBetween(stockCode, oldest, newest)
            .forEach(row -> existingByDate.put(row.getTradeDate(), row));

        UpsertSummary summary = UpsertSummary.EMPTY;
        for (StockInvestorTradingRecord record : records) {
            if (record.foreigner() == null || record.institution() == null) {
                log.warn("종목별 투자자 매매동향 레코드 스킵(외국인/기관 누락): stockCode={}, date={}",
                    stockCode, record.date());
                continue;
            }
            summary = summary.plus(upsert(stockCode, record, existingByDate.get(LocalDate.parse(record.date()))));
        }
        return summary;
    }

    private UpsertSummary upsert(String stockCode, StockInvestorTradingRecord record,
                                 @Nullable StockInvestorTrading existing) {
        LocalDateTime sourceUpdatedAt = OffsetDateTime.parse(record.updatedAt()).toLocalDateTime();
        InvestorVolume individual = toNullableVolume(record.individual());
        InvestorVolume foreigner = toVolume(record.foreigner());
        InvestorVolume institution = toInstitutionVolume(record.institution());
        InstitutionBreakdown breakdown = toBreakdown(record.institution().breakdown());
        InvestorVolume otherCorporation = toNullableVolume(record.otherCorporation());
        ForeignerHolding holding = record.foreignerHolding();
        Long holdingQuantity = holding == null ? null : Long.parseLong(holding.holdingQuantity());
        Double holdingRate = holding == null ? null : Double.parseDouble(holding.holdingRate());

        if (existing == null) {
            stockInvestorTradingAppender.save(StockInvestorTrading.of(stockCode, LocalDate.parse(record.date()),
                sourceUpdatedAt, individual, foreigner, institution, breakdown, otherCorporation, holdingQuantity,
                holdingRate));
            return UpsertSummary.SAVED;
        }
        // 같은 updatedAt이어도 기관 세부 컬럼이 비어 있는 옛 행(세부 저장 이전 백필분)은 응답에 세부가 있으면 채운다.
        boolean sameStamp = existing.getSourceUpdatedAt().isEqual(sourceUpdatedAt);
        boolean breakdownMissing = existing.lacksInstitutionBreakdown() && breakdown != null;
        if (sameStamp && !breakdownMissing) {
            return UpsertSummary.SKIPPED;
        }
        existing.updateFrom(sourceUpdatedAt, individual, foreigner, institution, breakdown, otherCorporation,
            holdingQuantity, holdingRate);
        stockInvestorTradingAppender.save(existing);
        return UpsertSummary.UPDATED;
    }

    // 잠정 기록에서 null인 분류(개인·기타법인)는 null 그대로 둔다 - 0으로 채우면 "순매수 0"으로 오해된다.
    @Nullable
    private InvestorVolume toNullableVolume(@Nullable InvestorTradingVolume volume) {
        return volume == null ? null : toVolume(volume);
    }

    private InvestorVolume toInstitutionVolume(InstitutionTradingVolume institution) {
        return InvestorVolume.of(
            Long.parseLong(institution.buyVolume()),
            Long.parseLong(institution.sellVolume()),
            Long.parseLong(institution.netBuyVolume()));
    }

    // 세부는 잠정 기록에서 null이고, 있으면 7개가 모두 온다(스펙 required).
    @Nullable
    private InstitutionBreakdown toBreakdown(
        @Nullable TossStockInvestorTradingResponse.InstitutionBreakdown breakdown) {
        if (breakdown == null) {
            return null;
        }
        return InstitutionBreakdown.builder()
            .financialInvestmentNetBuyVolume(netOf(breakdown.financialInvestment()))
            .insuranceNetBuyVolume(netOf(breakdown.insurance()))
            .trustNetBuyVolume(netOf(breakdown.trust()))
            .privateEquityFundNetBuyVolume(netOf(breakdown.privateEquityFund()))
            .bankNetBuyVolume(netOf(breakdown.bank()))
            .otherFinancialInstitutionNetBuyVolume(netOf(breakdown.otherFinancialInstitution()))
            .pensionFundNetBuyVolume(netOf(breakdown.pensionFund()))
            .build();
    }

    private Long netOf(InvestorTradingVolume volume) {
        return Long.parseLong(volume.netBuyVolume());
    }

    private InvestorVolume toVolume(InvestorTradingVolume volume) {
        return InvestorVolume.of(
            Long.parseLong(volume.buyVolume()),
            Long.parseLong(volume.sellVolume()),
            Long.parseLong(volume.netBuyVolume()));
    }

    /** 한 번의 수집이 신규 저장/갱신/스킵한 행 수. */
    public record UpsertSummary(int saved, int updated, int skipped) {

        static final UpsertSummary EMPTY = new UpsertSummary(0, 0, 0);
        static final UpsertSummary SAVED = new UpsertSummary(1, 0, 0);
        static final UpsertSummary UPDATED = new UpsertSummary(0, 1, 0);
        static final UpsertSummary SKIPPED = new UpsertSummary(0, 0, 1);

        public UpsertSummary plus(UpsertSummary other) {
            return new UpsertSummary(saved + other.saved, updated + other.updated, skipped + other.skipped);
        }
    }
}
