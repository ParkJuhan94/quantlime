package com.quantlime.score.repository;

import com.quantlime.price.domain.StockLiquidity;
import com.quantlime.price.repository.StockLiquidityRepository;
import com.quantlime.score.domain.Divergence;
import com.quantlime.score.domain.PeerGroup;
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

    @Autowired
    private StockLiquidityRepository stockLiquidityRepository;

    private Score score(String stockCode, LocalDate scoreDate, double compositePercentile) {
        Score score = Score.of(stockCode, scoreDate, 50.0, 50.0, 50.0, null,
            null, Divergence.of(false, null), false);
        // v3.0부터 랭킹 정렬은 compositePercentile을 쓴다 - 정규화 단계
        // 없이도 정렬 기준 값을 직접 채워 이 리포지토리 계층만 검증한다.
        score.applyNormalization(compositePercentile, compositePercentile,
            compositePercentile, PeerGroup.DOMESTIC);
        return score;
    }

    // v3.0부터 랭킹 조회는 stock(상장·가격지원)과 stock_liquidity(유동성)를
    // 이너 조인한다 - 이 둘을 세팅하지 않으면 그 종목은 대상에서 항상
    // 빠진다(2026-09 감사 세션, 잡주 상위 독식 방지).
    private void seedEligibleStock(String stockCode, MarketType marketType) {
        stockRepository.save(Stock.of(stockCode, stockCode + " 종목명", marketType, ListingStatus.LISTED, "업종"));
        stockLiquidityRepository.save(
            StockLiquidity.of(stockCode, LocalDate.now(), 2_000_000_000.0, 0, true));
    }

    @Test
    @DisplayName("[전 종목 중 각 종목의 최신 스코어만 골라 종합백분위 내림차순 상위 N개를 반환한다]")
    void findTopScoresOrderByCompositeScoreDesc_returnsLatestPerStockTopN() {
        // given: 005930은 어제(60점)·오늘(90점) 두 건 - 최신(오늘) 것만 잡혀야 한다.
        seedEligibleStock("005930", MarketType.KOSPI);
        seedEligibleStock("000660", MarketType.KOSPI);
        seedEligibleStock("035420", MarketType.KOSPI);
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 17), 60.0));
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 18), 90.0));
        scoreRepository.save(score("000660", LocalDate.of(2026, 7, 18), 80.0));
        scoreRepository.save(score("035420", LocalDate.of(2026, 7, 18), 70.0));

        // when: marketTypes 필터 없음(null) - 전체 대상
        List<Score> result = scoreRepository.findTopScoresOrderByCompositeScoreDesc(2, null);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStockCode()).isEqualTo("005930");
        assertThat(result.get(0).getCompositePercentile()).isEqualTo(90.0);
        assertThat(result.get(1).getStockCode()).isEqualTo("000660");
    }

    @Test
    @DisplayName("[marketTypes를 지정하면 그 시장에 속한 종목만 대상으로 정렬한다 - "
        + "국내/해외를 안 가리면 스코어 분포 차이로 한쪽 시장에만 쏠리는 문제(2026-07-30 실제 버그)의 회귀 테스트]")
    void findTopScoresOrderByCompositeScoreDesc_filtersByMarketType() {
        // given: 해외(AAPL) 백분위가 국내(005930)보다 훨씬 높아도, domestic만 지정하면 AAPL은 제외돼야 한다
        seedEligibleStock("005930", MarketType.KOSPI);
        seedEligibleStock("AAPL", MarketType.NASDAQ);
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 18), 60.0));
        scoreRepository.save(score("AAPL", LocalDate.of(2026, 7, 18), 95.0));

        // when
        List<Score> domesticOnly = scoreRepository
            .findTopScoresOrderByCompositeScoreDesc(10, MarketType.domesticValues());

        // then
        assertThat(domesticOnly).extracting(Score::getStockCode).containsExactly("005930");
    }

    @Test
    @DisplayName("[상장폐지·가격미지원·저유동성 종목은 scope=all이어도 랭킹에서 제외된다]")
    void findTopScoresOrderByCompositeScoreDesc_excludesIneligibleStocks() {
        // given: 정상 종목 하나 + 상장폐지/저유동성 종목 각각 하나씩(모두 백분위는 더 높게)
        seedEligibleStock("005930", MarketType.KOSPI);
        scoreRepository.save(score("005930", LocalDate.of(2026, 7, 18), 50.0));

        stockRepository.save(Stock.of("999001", "상장폐지종목", MarketType.KOSDAQ, ListingStatus.DELISTED, "업종"));
        stockLiquidityRepository.save(StockLiquidity.of("999001", LocalDate.now(), 2_000_000_000.0, 0, true));
        scoreRepository.save(score("999001", LocalDate.of(2026, 7, 18), 99.0));

        stockRepository.save(Stock.of("999002", "저유동성종목", MarketType.KOSDAQ, ListingStatus.LISTED, "업종"));
        stockLiquidityRepository.save(StockLiquidity.of("999002", LocalDate.now(), 100.0, 20, false));
        scoreRepository.save(score("999002", LocalDate.of(2026, 7, 18), 98.0));

        // when: marketTypes 필터 없음(scope=all)
        List<Score> result = scoreRepository.findTopScoresOrderByCompositeScoreDesc(10, null);

        // then
        assertThat(result).extracting(Score::getStockCode).containsExactly("005930");
    }
}
