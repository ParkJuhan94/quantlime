package com.quantlime.price.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
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

        // 상관 서브쿼리 대신 비상관 튜플 IN 서브쿼리 사용 - 2026-08-19 실측,
        // ScoreQueryRepositoryImpl.latestScoreDateTuple 주석 참고. 전일종가
        // 조회가 관심종목 전체(수천 개)를 한 번에 묻는 경로라 상관 버전은
        // 종목 수가 늘수록 초선형으로 느려진다(2,596종목 기준 60.3초 실측).
        JPAQuery<Tuple> latestDates = queryFactory
            .select(latest.stockCode, latest.tradeDate.max())
            .from(latest)
            .where(latest.stockCode.in(stockCodes), latest.tradeDate.lt(date))
            .groupBy(latest.stockCode);

        return queryFactory
            .selectFrom(domesticDailyPrice)
            .where(
                domesticDailyPrice.stockCode.in(stockCodes),
                domesticDailyPrice.tradeDate.lt(date),
                Expressions.list(domesticDailyPrice.stockCode, domesticDailyPrice.tradeDate).in(latestDates)
            )
            .fetch();
    }
}
