package com.quantlime.backtest.repository;

import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.support.DataJpaTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("integration")
class BacktestDailyScoreRepositoryTest extends DataJpaTestSupport {

    private static final String STOCK_CODE = "005930";
    private static final String SCORE_VERSION = "v2.1";

    @Autowired
    private BacktestDailyScoreRepository backtestDailyScoreRepository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * 실제 라이브 재실행에서 재현됐던 버그의 회귀 테스트 - 파생 delete
     * 메서드(deleteBy...)는 대상을 로드해 entityManager.remove()만 호출하고
     * 실제 DELETE는 플러시 시점까지 미루는데, Hibernate는 같은 플러시 안에서
     * INSERT를 DELETE보다 먼저 실행한다. 그래서 "삭제 후 같은 키로 재삽입"이
     * BacktestPersistenceService.replaceDailyScores()의 실제 사용 패턴인데,
     * 재삽입(INSERT)이 아직 DB에 남아있는 이전 행과 유니크 제약이 충돌해
     * ConstraintViolationException이 났었다. @Modifying 벌크 delete로 바꿔
     * 즉시 실행되게 고쳤다.
     */
    @Test
    @DisplayName("[같은 트랜잭션에서 삭제 후 겹치는 키로 재삽입해도 유니크 제약 위반이 나지 않는다]")
    void deleteByStockCodeAndScoreVersion_thenReinsertSameKeys_doesNotViolateUniqueConstraint() {
        // given
        backtestDailyScoreRepository.save(dailyScore(LocalDate.of(2026, 1, 1), 100.0));
        entityManager.flush();
        entityManager.clear();

        // when: BacktestPersistenceService.replaceDailyScores와 동일한 순서
        backtestDailyScoreRepository.deleteByStockCodeAndScoreVersion(STOCK_CODE, SCORE_VERSION);
        backtestDailyScoreRepository.saveAll(List.of(dailyScore(LocalDate.of(2026, 1, 1), 105.0)));

        // then
        assertThatCode(() -> entityManager.flush()).doesNotThrowAnyException();
        List<BacktestDailyScore> result = backtestDailyScoreRepository
            .findByStockCodeAndScoreVersionOrderByTradeDateAsc(STOCK_CODE, SCORE_VERSION);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClosePrice()).isEqualTo(105.0);
    }

    @Test
    @DisplayName("[다른 종목/버전의 행은 삭제되지 않는다]")
    void deleteByStockCodeAndScoreVersion_doesNotAffectOtherStockOrVersion() {
        // given
        backtestDailyScoreRepository.save(dailyScore(LocalDate.of(2026, 1, 1), 100.0));
        BacktestDailyScore otherVersion = BacktestDailyScore.of(
            STOCK_CODE, "v2.2", LocalDate.of(2026, 1, 1), 100.0, null, null, null, null);
        backtestDailyScoreRepository.save(otherVersion);
        entityManager.flush();

        // when
        backtestDailyScoreRepository.deleteByStockCodeAndScoreVersion(STOCK_CODE, SCORE_VERSION);
        entityManager.flush();

        // then
        assertThat(backtestDailyScoreRepository
            .findByStockCodeAndScoreVersionOrderByTradeDateAsc(STOCK_CODE, SCORE_VERSION)).isEmpty();
        assertThat(backtestDailyScoreRepository
            .findByStockCodeAndScoreVersionOrderByTradeDateAsc(STOCK_CODE, "v2.2")).hasSize(1);
    }

    private BacktestDailyScore dailyScore(LocalDate tradeDate, Double closePrice) {
        return BacktestDailyScore.of(
            STOCK_CODE, SCORE_VERSION, tradeDate, closePrice, null, null, null, null);
    }
}
