package com.quantlime.price.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.domain.QDailyPrice;
import com.quantlime.price.dto.StockTradingValue;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailyPriceQueryRepositoryImpl implements DailyPriceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<StockTradingValue> findTopByTradingValue(LocalDate since, int limit) {
        QDailyPrice dailyPrice = QDailyPrice.dailyPrice;

        return queryFactory
            .select(Projections.constructor(StockTradingValue.class,
                dailyPrice.stockCode,
                dailyPrice.closePrice.multiply(dailyPrice.volume).sum()))
            .from(dailyPrice)
            .where(dailyPrice.tradeDate.goe(since))
            .groupBy(dailyPrice.stockCode)
            .orderBy(dailyPrice.closePrice.multiply(dailyPrice.volume).sum().desc())
            .limit(limit)
            .fetch();
    }

    @Override
    public List<DailyPrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date) {
        QDailyPrice dailyPrice = QDailyPrice.dailyPrice;
        QDailyPrice latest = new QDailyPrice("latest");

        if (stockCodes.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(dailyPrice)
            .where(
                dailyPrice.stockCode.in(stockCodes),
                dailyPrice.tradeDate.lt(date),
                dailyPrice.tradeDate.eq(latestTradeDateSubquery(latest, dailyPrice, date))
            )
            .fetch();
    }

    private JPQLQuery<LocalDate> latestTradeDateSubquery(
        QDailyPrice latest, QDailyPrice dailyPrice, LocalDate date) {
        return JPAExpressions
            .select(latest.tradeDate.max())
            .from(latest)
            .where(latest.stockCode.eq(dailyPrice.stockCode), latest.tradeDate.lt(date));
    }
}
