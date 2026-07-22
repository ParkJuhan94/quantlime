package com.quantlime.score.repository;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.quantlime.score.domain.QScore;
import com.quantlime.score.domain.Score;
import java.time.LocalDate;
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
                score.scoreDate.eq(latestScoreDateSubquery(latest, score))
            )
            .orderBy(score.compositeScore.desc().nullsLast())
            .fetch();
    }

    @Override
    public List<Score> findTopScoresOrderByCompositeScoreDesc(int limit) {
        QScore score = QScore.score;
        QScore latest = new QScore("latest");

        return queryFactory
            .selectFrom(score)
            .where(score.scoreDate.eq(latestScoreDateSubquery(latest, score)))
            .orderBy(score.compositeScore.desc().nullsLast())
            .limit(limit)
            .fetch();
    }

    private JPQLQuery<LocalDate> latestScoreDateSubquery(QScore latest, QScore score) {
        return JPAExpressions
            .select(latest.scoreDate.max())
            .from(latest)
            .where(latest.stockCode.eq(score.stockCode));
    }
}
