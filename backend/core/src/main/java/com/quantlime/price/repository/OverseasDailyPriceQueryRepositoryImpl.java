package com.quantlime.price.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.domain.QOverseasDailyPrice;
import com.quantlime.price.dto.OverseasStockTradingValue;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OverseasDailyPriceQueryRepositoryImpl implements OverseasDailyPriceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OverseasStockTradingValue> findTopByTradingValue(LocalDate since, int limit) {
        QOverseasDailyPrice overseasDailyPrice = QOverseasDailyPrice.overseasDailyPrice;

        return queryFactory
            .select(Projections.constructor(OverseasStockTradingValue.class,
                overseasDailyPrice.stockCode,
                overseasDailyPrice.closePrice.multiply(overseasDailyPrice.volume).sum()))
            .from(overseasDailyPrice)
            .where(overseasDailyPrice.tradeDate.goe(since))
            .groupBy(overseasDailyPrice.stockCode)
            .orderBy(overseasDailyPrice.closePrice.multiply(overseasDailyPrice.volume).sum().desc())
            .limit(limit)
            .fetch();
    }

    @Override
    public List<OverseasDailyPrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date) {
        QOverseasDailyPrice overseasDailyPrice = QOverseasDailyPrice.overseasDailyPrice;
        QOverseasDailyPrice latest = new QOverseasDailyPrice("latest");

        if (stockCodes.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(overseasDailyPrice)
            .where(
                overseasDailyPrice.stockCode.in(stockCodes),
                overseasDailyPrice.tradeDate.lt(date),
                overseasDailyPrice.tradeDate.eq(latestTradeDateSubquery(latest, overseasDailyPrice, date))
            )
            .fetch();
    }

    private JPQLQuery<LocalDate> latestTradeDateSubquery(
        QOverseasDailyPrice latest, QOverseasDailyPrice overseasDailyPrice, LocalDate date) {
        return JPAExpressions
            .select(latest.tradeDate.max())
            .from(latest)
            .where(latest.stockCode.eq(overseasDailyPrice.stockCode), latest.tradeDate.lt(date));
    }
}
