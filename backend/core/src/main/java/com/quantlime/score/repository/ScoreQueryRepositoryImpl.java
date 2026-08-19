package com.quantlime.score.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlime.score.domain.QScore;
import com.quantlime.score.domain.Score;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.QStock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScoreQueryRepositoryImpl implements ScoreQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Score> findLatestScoresByStockCodesOrderByCompositeScoreDesc(
        List<String> stockCodes) {
        QScore score = QScore.score;
        QScore latest = new QScore("latest");

        if (stockCodes.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(score)
            .where(
                score.stockCode.in(stockCodes),
                latestScoreDateTuple(latest, stockCodes)
            )
            .orderBy(score.compositeScore.desc().nullsLast())
            .fetch();
    }

    @Override
    public List<Score> findTopScoresOrderByCompositeScoreDesc(int limit, List<MarketType> marketTypes) {
        QScore score = QScore.score;
        QScore latest = new QScore("latest");
        QStock stock = QStock.stock;

        JPAQuery<Score> query = queryFactory
            .selectFrom(score)
            .where(latestScoreDateTuple(latest, null));

        if (marketTypes != null && !marketTypes.isEmpty()) {
            query.where(score.stockCode.in(
                JPAExpressions.select(stock.stockCode)
                    .from(stock)
                    .where(stock.marketType.in(marketTypes))));
        }

        return query
            .orderBy(score.compositeScore.desc().nullsLast())
            .limit(limit)
            .fetch();
    }

    /**
     * "종목별 최신 score_date" 필터를 상관 서브쿼리(행마다 재실행)가 아니라
     * 비상관 튜플 IN 서브쿼리로 표현한다 - 2026-08-19 실측: 상관 서브쿼리는
     * 205만 행 전체를 훑으며 행마다 서브쿼리를 재실행해 116.7초, 이 튜플 IN
     * 형태는 서브쿼리를 한 번만 실행(해시 인덱스로 materialize)해 약 1.3초로
     * 단축된다(약 90배). JPQL은 FROM 절에 서브쿼리(파생 테이블)를 허용하지
     * 않아 raw SQL로 검증한 파생 테이블 조인(0.82초)만큼은 못 줄이지만,
     * 기존 QueryDSL 컨벤션을 벗어나지 않고 얻을 수 있는 최선이다.
     */
    private BooleanExpression latestScoreDateTuple(QScore latest, List<String> stockCodes) {
        JPAQuery<Tuple> latestDates = queryFactory
            .select(latest.stockCode, latest.scoreDate.max())
            .from(latest)
            .groupBy(latest.stockCode);
        if (stockCodes != null) {
            latestDates.where(latest.stockCode.in(stockCodes));
        }
        return Expressions.list(QScore.score.stockCode, QScore.score.scoreDate).in(latestDates);
    }
}
