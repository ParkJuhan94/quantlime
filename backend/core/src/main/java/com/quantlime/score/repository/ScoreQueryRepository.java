package com.quantlime.score.repository;

import com.quantlime.score.domain.Score;
import java.util.List;

public interface ScoreQueryRepository {

    /**
     * 주어진 종목 코드들 중 각 종목의 가장 최근(score_date 최대) 스코어 행만
     * 골라 종합점수(compositeScore) 내림차순으로 반환한다. 대시보드 랭킹용.
     */
    List<Score> findLatestScoresByStockCodesOrderByCompositeScoreDesc(List<String> stockCodes);

    /**
     * 관심종목 여부와 무관하게 전 상장종목 중 각 종목의 가장 최근 스코어
     * 행만 골라 종합점수 내림차순 상위 N개를 반환한다("실시간 랭킹"의
     * 스코어 탭 - 관심종목만/전체 토글 중 "전체" 쪽, 2026-07-18).
     */
    List<Score> findTopScoresOrderByCompositeScoreDesc(int limit);
}
