package com.quantlime.price.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.QDomesticDailyPrice;
import com.quantlime.price.dto.DomesticStockTradingValue;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DomesticDailyPriceQueryRepositoryImpl implements DomesticDailyPriceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<DomesticStockTradingValue> findTopByTradingValue(LocalDate since, int limit) {
        QDomesticDailyPrice domesticDailyPrice = QDomesticDailyPrice.domesticDailyPrice;

        return queryFactory
            .select(Projections.constructor(DomesticStockTradingValue.class,
                domesticDailyPrice.stockCode,
                domesticDailyPrice.closePrice.multiply(domesticDailyPrice.volume).sum()))
            .from(domesticDailyPrice)
            .where(domesticDailyPrice.tradeDate.goe(since))
            .groupBy(domesticDailyPrice.stockCode)
            .orderBy(domesticDailyPrice.closePrice.multiply(domesticDailyPrice.volume).sum().desc())
            .limit(limit)
            .fetch();
    }

    @Override
    public List<DomesticDailyPrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date) {
        QDomesticDailyPrice domesticDailyPrice = QDomesticDailyPrice.domesticDailyPrice;
        QDomesticDailyPrice latest = new QDomesticDailyPrice("latest");

        if (stockCodes.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(domesticDailyPrice)
            .where(
                domesticDailyPrice.stockCode.in(stockCodes),
                domesticDailyPrice.tradeDate.lt(date),
                domesticDailyPrice.tradeDate.eq(latestTradeDateSubquery(latest, domesticDailyPrice, date))
            )
            .fetch();
    }

    private JPQLQuery<LocalDate> latestTradeDateSubquery(
        QDomesticDailyPrice latest, QDomesticDailyPrice domesticDailyPrice, LocalDate date) {
        return JPAExpressions
            .select(latest.tradeDate.max())
            .from(latest)
            .where(latest.stockCode.eq(domesticDailyPrice.stockCode), latest.tradeDate.lt(date));
    }
}
