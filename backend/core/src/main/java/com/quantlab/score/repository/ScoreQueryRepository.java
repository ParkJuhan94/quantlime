package com.quantlab.score.repository;

import com.quantlab.score.domain.Score;
import java.util.List;

public interface ScoreQueryRepository {

    /**
     * 주어진 종목 코드들 중 각 종목의 가장 최근(score_date 최대) 스코어 행만
     * 골라 종합점수(compositeScore) 내림차순으로 반환한다. 대시보드 랭킹용.
     */
    List<Score> findLatestScoresByStockCodesOrderByCompositeScoreDesc(List<String> stockCodes);
}
