package com.quantlime.price.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlime.price.domain.DomesticRegularClosePrice;
import com.quantlime.price.domain.QDomesticRegularClosePrice;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DomesticRegularClosePriceQueryRepositoryImpl implements DomesticRegularClosePriceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<DomesticRegularClosePrice> findLatestBeforeDate(List<String> stockCodes, LocalDate date) {
        QDomesticRegularClosePrice regularClose = QDomesticRegularClosePrice.domesticRegularClosePrice;
        QDomesticRegularClosePrice latest = new QDomesticRegularClosePrice("latest");

        if (stockCodes.isEmpty()) {
            return List.of();
        }

        // 상관 서브쿼리 대신 비상관 튜플 IN 서브쿼리 사용 - 2026-08-19 실측,
        // ScoreQueryRepositoryImpl.latestScoreDateTuple 주석 참고. 이 메서드가
        // DomesticPreviousCloseResolver를 통해 매일 정규장 종가 조회에서
        // 관심종목 전체(스윕 대상 최대 2,596종목)를 한 번에 묻는 1순위 경로다.
        JPAQuery<Tuple> latestDates = queryFactory
            .select(latest.stockCode, latest.tradeDate.max())
            .from(latest)
            .where(latest.stockCode.in(stockCodes), latest.tradeDate.lt(date))
            .groupBy(latest.stockCode);

        return queryFactory
            .selectFrom(regularClose)
            .where(
                regularClose.stockCode.in(stockCodes),
                regularClose.tradeDate.lt(date),
                Expressions.list(regularClose.stockCode, regularClose.tradeDate).in(latestDates)
            )
            .fetch();
    }
}
