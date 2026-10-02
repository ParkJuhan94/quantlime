package com.quantlime.score.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

    private Score scoreWithComposite(String stockCode, LocalDate scoreDate, Double compositeScore) {
        return Score.of(stockCode, scoreDate, 50.0, 50.0, compositeScore, null,
            null, Divergence.of(false, null), false);
    }

    @Test
    @DisplayName("[관심종목 경로는 종목별 최신 날짜 행만 종합점수 내림차순으로(점수 없음은 맨 뒤) 반환하고 요청 밖 종목은 제외한다]")
    void findLatestScoresByStockCodes_returnsLatestPerStockOrderedByCompositeNullsLast() {
        // given
        LocalDate today = LocalDate.now();
        scoreRepository.save(scoreWithComposite("A", today.minusDays(3), 10.0)); // A의 옛 행(제외돼야 함)
        scoreRepository.save(scoreWithComposite("A", today, 70.0));
        scoreRepository.save(scoreWithComposite("B", today, 90.0));
        scoreRepository.save(scoreWithComposite("C", today, null));
        scoreRepository.save(scoreWithComposite("D", today, 99.0)); // 요청에 없는 종목

        // when
        List<Score> result = scoreRepository.findLatestScoresByStockCodesOrderByCompositeScoreDesc(
            List.of("A", "B", "C"));

        // then
        assertThat(result).extracting(Score::getStockCode).containsExactly("B", "A", "C");
        assertThat(result.get(1).getScoreDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("[종목코드가 비어 있으면 쿼리 없이 빈 목록이다]")
    void findLatestScoresByStockCodes_emptyCodes_returnsEmpty() {
        assertThat(scoreRepository.findLatestScoresByStockCodesOrderByCompositeScoreDesc(List.of())).isEmpty();
    }

    @Test
    @DisplayName("[정규화 모집단은 적격 종목의 최신 배치일 행만이고 옛 날짜 행·부적격(유동성 없음) 종목은 제외한다]")
    void findLatestScoresForNormalization_returnsLatestBatchOfEligibleStocksOnly() {
        // given
        LocalDate today = LocalDate.now();
        seedEligibleStock("005930", MarketType.KOSPI);
        seedEligibleStock("AAPL", MarketType.NASDAQ);
        stockRepository.save(Stock.of("111111", "유동성없음", MarketType.KOSPI, ListingStatus.LISTED, "업종"));
        scoreRepository.save(score("005930", today.minusDays(1), 10.0)); // 최신 배치일이 아님
        scoreRepository.save(score("005930", today, 60.0));
        scoreRepository.save(score("AAPL", today, 40.0));
        scoreRepository.save(score("111111", today, 99.0)); // stock_liquidity 행이 없어 부적격

        // when
        List<Score> all = scoreRepository.findLatestScoresForNormalization(null);
        List<Score> domesticOnly = scoreRepository.findLatestScoresForNormalization(List.of(MarketType.KOSPI));
        List<Score> emptyFilter = scoreRepository.findLatestScoresForNormalization(List.of());

        // then
        assertThat(all).extracting(Score::getStockCode).containsExactlyInAnyOrder("005930", "AAPL");
        assertThat(all).allSatisfy(score -> assertThat(score.getScoreDate()).isEqualTo(today));
        assertThat(domesticOnly).extracting(Score::getStockCode).containsExactly("005930");
        assertThat(emptyFilter).extracting(Score::getStockCode).containsExactlyInAnyOrder("005930", "AAPL");
    }

    @Test
    @DisplayName("[적격 종목이 없거나 스코어 행이 하나도 없으면 정규화 모집단은 빈 목록이다]")
    void findLatestScoresForNormalization_noEligibleStocksOrNoScores_returnsEmpty() {
        // 적격 종목 없음(스코어만 있음)
        scoreRepository.save(score("005930", LocalDate.now(), 60.0));
        assertThat(scoreRepository.findLatestScoresForNormalization(null)).isEmpty();

        // 적격 종목은 있지만 해당 종목 스코어 행이 최신 배치일에 없음
        seedEligibleStock("000660", MarketType.KOSPI);
        assertThat(scoreRepository.findLatestScoresForNormalization(List.of(MarketType.KOSPI))).isEmpty();
    }

    @Test
    @DisplayName("[특정 날짜 이하 중 종목별 최신 행을 돌려준다(그 이후 행은 무시, 코드가 비면 빈 목록)]")
    void findLatestScoresOnOrBefore_returnsLatestRowOnOrBeforeDate() {
        // given
        LocalDate today = LocalDate.now();
        scoreRepository.save(scoreWithComposite("A", today.minusDays(5), 30.0));
        scoreRepository.save(scoreWithComposite("A", today.minusDays(1), 80.0)); // 기준일 이후 -> 무시
        scoreRepository.save(scoreWithComposite("B", today.minusDays(3), 55.0));

        // when
        List<Score> result = scoreRepository.findLatestScoresOnOrBefore(List.of("A", "B"), today.minusDays(3));

        // then: A는 기준일 이하 중 최신(-5일), B는 기준일 당일(-3일) 행
        assertThat(result).extracting(Score::getStockCode, Score::getScoreDate)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("A", today.minusDays(5)),
                org.assertj.core.groups.Tuple.tuple("B", today.minusDays(3)));
        assertThat(scoreRepository.findLatestScoresOnOrBefore(List.of(), today)).isEmpty();
    }

    @Test
    @DisplayName("[종목별 최신 산출일 맵은 각 종목의 가장 늦은 score_date다]")
    void findLatestScoreDateByStockCode_returnsMaxDatePerStock() {
        LocalDate today = LocalDate.now();
        scoreRepository.save(scoreWithComposite("A", today.minusDays(4), 10.0));
        scoreRepository.save(scoreWithComposite("A", today.minusDays(2), 20.0));
        scoreRepository.save(scoreWithComposite("B", today.minusDays(7), 30.0));

        Map<String, LocalDate> result = scoreRepository.findLatestScoreDateByStockCode();

        assertThat(result).containsOnly(
            Map.entry("A", today.minusDays(2)), Map.entry("B", today.minusDays(7)));
    }
}
