package com.quantlime.score.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlime.price.domain.QStockLiquidity;
import com.quantlime.score.domain.QScore;
import com.quantlime.score.domain.Score;
import com.quantlime.stock.domain.ListingStatus;
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

        List<String> eligibleStockCodes = eligibleStockCodes(marketTypes);
        if (eligibleStockCodes.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(score)
            .where(
                score.stockCode.in(eligibleStockCodes),
                latestScoreDateTuple(latest, null))
            .orderBy(score.compositePercentile.desc().nullsLast())
            .limit(limit)
            .fetch();
    }

    @Override
    public List<Score> findLatestScoresForNormalization(List<MarketType> marketTypes) {
        QScore score = QScore.score;
        QScore latest = new QScore("latest");

        List<String> eligibleStockCodes = eligibleStockCodes(marketTypes);
        if (eligibleStockCodes.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(score)
            .where(
                score.stockCode.in(eligibleStockCodes),
                latestScoreDateTuple(latest, null))
            .fetch();
    }

    /**
     * 상장·가격지원·유동성 조건을 모두 만족하는 종목코드만 - 랭킹 조회와
     * 횡단면 정규화 모집단이 같은 기준을 공유한다(둘 다 잡주를 걸러내야
     * 하므로, 2026-09 감사 세션). v3.0부터 항상 stock을 조인한다(이전엔
     * scope=all일 때 marketTypes가 null/빈 리스트라 조인 자체가 없어
     * DELISTED/price_unsupported 종목의 스코어도 그대로 섞일 수 있었다 -
     * 방어 누락 발견). 유동성 필터(stock_liquidity.liquid)도 scope 무관하게
     * 항상 적용한다 - 잡주를 걸러내는 게 이번 감사의 핵심 목적이라 scope로
     * 우회할 수 있으면 안 된다.
     */
    private List<String> eligibleStockCodes(List<MarketType> marketTypes) {
        QStock stock = QStock.stock;
        BooleanExpression stockFilter = stock.listingStatus.eq(ListingStatus.LISTED)
            .and(stock.priceUnsupported.isFalse());
        if (marketTypes != null && !marketTypes.isEmpty()) {
            stockFilter = stockFilter.and(stock.marketType.in(marketTypes));
        }

        return queryFactory
            .select(stock.stockCode)
            .from(stock)
            .innerJoin(QStockLiquidity.stockLiquidity)
            .on(QStockLiquidity.stockLiquidity.stockCode.eq(stock.stockCode))
            .where(stockFilter, QStockLiquidity.stockLiquidity.liquid.isTrue())
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
