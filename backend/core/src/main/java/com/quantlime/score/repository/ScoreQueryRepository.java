package com.quantlime.score.repository;

import com.quantlime.score.domain.Score;
import com.quantlime.stock.domain.MarketType;
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
     *
     * @param marketTypes null/빈 리스트면 시장 구분 없이 전체(국내+해외를
     *                     그대로 섞어 정렬) - 지정하면 그 시장에 속한
     *                     종목만 대상으로 정렬한다(2026-07-30 추가 - 이
     *                     필터가 없으면 국내/해외 스코어 분포 차이 때문에
     *                     상위 N개가 한쪽 시장으로 쏠리는 문제가 있었음).
     */
    List<Score> findTopScoresOrderByCompositeScoreDesc(int limit, List<MarketType> marketTypes);
}
