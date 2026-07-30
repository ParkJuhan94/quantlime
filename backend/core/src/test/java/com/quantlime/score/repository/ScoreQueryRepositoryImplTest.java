package com.quantlime.score.repository;

import com.quantlime.score.domain.Divergence;
import com.quantlime.score.domain.Grade;
import com.quantlime.score.domain.Score;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.support.DataJpaTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ScoreQueryRepositoryImplTest extends DataJpaTestSupport {

    @Autowired
    private ScoreRepository scoreRepository;

    @Autowired
    private StockRepository stockRepository;

    private Score score(String stockCode, LocalDate scoreDate, double compositeScore) {
        return Score.of(stockCode, scoreDate, 50.0, 50.0, compositeScore, Grade.NEUTRAL,
            null, Divergence.of(false, null), false);
    }

    @Test
    @DisplayName("[전 종목 중 각 종목의 최신 스코어만 골라 종합점수 내림차순 상위 N개를 반환한다]")
    void findTopScoresOrderByCompositeScoreDesc_returnsLatestPerStockTopN() {
        // given: 005930은 어제(60점)·오늘(90점) 두 건 - 최신(오늘) 것만 잡혀야 한다.
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 17), 60.0));
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 18), 90.0));
        scoreRepository.save(score("000660", LocalDate.of(2026, 7, 18), 80.0));
        scoreRepository.save(score("035420", LocalDate.of(2026, 7, 18), 70.0));

        // when: marketTypes 필터 없음(null) - 전체 대상
        List<Score> result = scoreRepository.findTopScoresOrderByCompositeScoreDesc(2, null);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStockCode()).isEqualTo("005930");
        assertThat(result.get(0).getCompositeScore()).isEqualTo(90.0);
        assertThat(result.get(1).getStockCode()).isEqualTo("000660");
    }

    @Test
    @DisplayName("[marketTypes를 지정하면 그 시장에 속한 종목만 대상으로 정렬한다 - "
        + "국내/해외를 안 가리면 스코어 분포 차이로 한쪽 시장에만 쏠리는 문제(2026-07-30 실제 버그)의 회귀 테스트]")
    void findTopScoresOrderByCompositeScoreDesc_filtersByMarketType() {
        // given: 해외(AAPL) 점수가 국내(005930)보다 훨씬 높아도, domestic만 지정하면 AAPL은 제외돼야 한다
        stockRepository.save(Stock.of("005930", "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자"));
        stockRepository.save(Stock.of("AAPL", "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720"));
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 18), 60.0));
        scoreRepository.save(score("AAPL", LocalDate.of(2026, 7, 18), 95.0));

        // when
        List<Score> domesticOnly = scoreRepository
            .findTopScoresOrderByCompositeScoreDesc(10, MarketType.domesticValues());

        // then
        assertThat(domesticOnly).extracting(Score::getStockCode).containsExactly("005930");
    }
}
