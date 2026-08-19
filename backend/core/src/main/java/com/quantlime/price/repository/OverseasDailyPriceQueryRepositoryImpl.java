package com.quantlime.price.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
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

        // 상관 서브쿼리 대신 비상관 튜플 IN 서브쿼리 사용 - 2026-08-19 실측,
        // ScoreQueryRepositoryImpl.latestScoreDateTuple 주석 참고.
        JPAQuery<Tuple> latestDates = queryFactory
            .select(latest.stockCode, latest.tradeDate.max())
            .from(latest)
            .where(latest.stockCode.in(stockCodes), latest.tradeDate.lt(date))
            .groupBy(latest.stockCode);

        return queryFactory
            .selectFrom(overseasDailyPrice)
            .where(
                overseasDailyPrice.stockCode.in(stockCodes),
                overseasDailyPrice.tradeDate.lt(date),
                Expressions.list(overseasDailyPrice.stockCode, overseasDailyPrice.tradeDate).in(latestDates)
            )
            .fetch();
    }
}
