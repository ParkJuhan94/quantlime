package com.quantlime.score.repository;

import com.quantlime.score.domain.Score;
import com.quantlime.stock.domain.MarketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    /**
     * {@code marketTypes}(상장·가격지원·유동성 조건을 모두 만족하는
     * 종목만)의 종목별 최신 스코어 행을 limit/정렬 없이 전부 반환한다 -
     * 횡단면 정규화(quant-engine `/normalize/cross-section`) 입력 모집단으로
     * 쓰인다({@link com.quantlime.score.service.ScoreService#normalizeCrossSection}).
     */
    List<Score> findLatestScoresForNormalization(List<MarketType> marketTypes);

    /**
     * 전 종목의 "종목코드 → 최신 스코어 산출일" 맵을 한 번에 반환한다.
     * {@code MarketDataRefreshService}가 전종목 갱신 루프에서 종목마다
     * {@code scoreRepository.findTopByStockCodeOrderByScoreDateDesc}를
     * 개별 호출하던 것(하루 2회 × 국내+해외 약 9,000회 왕복)을 이 배치
     * 조회 하나로 대체한다(2026-09 성능 감사) - 루프 시작 전에 한 번만
     * 호출하고, 루프 안에서는 이 메서드가 반환한 맵을 참조만 하므로
     * 루프 도중 스코어 테이블에 쓰는 코드가 없는 한 안전하다(스코어
     * 재계산은 이 루프가 끝난 뒤에 실행됨).
     */
    Map<String, LocalDate> findLatestScoreDateByStockCode();
}
