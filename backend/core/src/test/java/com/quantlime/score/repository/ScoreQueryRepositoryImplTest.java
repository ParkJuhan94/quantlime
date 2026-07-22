package com.quantlime.score.repository;

import com.quantlime.score.domain.Divergence;
import com.quantlime.score.domain.Grade;
import com.quantlime.score.domain.Score;
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

    private Score score(String stockCode, LocalDate scoreDate, double compositeScore) {
        return Score.of(stockCode, scoreDate, 50.0, 50.0, compositeScore, Grade.NEUTRAL,
            null, Divergence.of(false, null), "코멘트", false);
    }

    @Test
    @DisplayName("[전 종목 중 각 종목의 최신 스코어만 골라 종합점수 내림차순 상위 N개를 반환한다]")
    void findTopScoresOrderByCompositeScoreDesc_returnsLatestPerStockTopN() {
        // given: 005930은 어제(60점)·오늘(90점) 두 건 - 최신(오늘) 것만 잡혀야 한다.
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 17), 60.0));
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 18), 90.0));
        scoreRepository.save(score("000660", LocalDate.of(2026, 7, 18), 80.0));
        scoreRepository.save(score("035420", LocalDate.of(2026, 7, 18), 70.0));

        // when
        List<Score> result = scoreRepository.findTopScoresOrderByCompositeScoreDesc(2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStockCode()).isEqualTo("005930");
        assertThat(result.get(0).getCompositeScore()).isEqualTo(90.0);
        assertThat(result.get(1).getStockCode()).isEqualTo("000660");
    }
}
