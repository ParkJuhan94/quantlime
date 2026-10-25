package com.quantlime.tradingflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.support.DataJpaTestSupport;
import com.quantlime.tradingflow.domain.InvestorVolume;
import com.quantlime.tradingflow.domain.StockInvestorTrading;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 같은 {@link InvestorVolume}을 분류마다 컬럼 이름만 바꿔 4번 포함하는 매핑(@AttributeOverride)과, 모든 컬럼이 null이면
 * 임베디드 객체 자체가 null로 읽히는 Hibernate 동작(잠정 기록의 개인·기타법인)은 실제 DB 없이는 확인할 수 없어 통합
 * 테스트로 고정한다.
 */
@Tag("integration")
class StockInvestorTradingRepositoryTest extends DataJpaTestSupport {

    private static final String STOCK = "005930";

    @Autowired
    private StockInvestorTradingRepository repository;

    private StockInvestorTrading confirmed(LocalDate date) {
        return StockInvestorTrading.of(STOCK, date, date.atTime(18, 5), InvestorVolume.of(100, 300, -200),
            InvestorVolume.of(500, 400, 100), InvestorVolume.of(300, 200, 100), InvestorVolume.of(10, 10, 0),
            1_000_000L, 0.5089);
    }

    private StockInvestorTrading provisional(LocalDate date) {
        return StockInvestorTrading.of(STOCK, date, date.atTime(14, 35), null, InvestorVolume.of(500, 400, 100),
            InvestorVolume.of(300, 200, 100), null, null, null);
    }

    @Test
    @DisplayName("[확정 기록은 분류별 매수·매도·순매수와 외국인 보유가 컬럼별로 따로 저장·조회된다]")
    void saveAndFind_confirmedRecord_roundTripsEveryGroup() {
        repository.saveAndFlush(confirmed(LocalDate.of(2026, 10, 23)));

        StockInvestorTrading found = repository
            .findByStockCodeAndTradeDateBetween(STOCK, LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 23))
            .get(0);

        assertThat(found.getIndividual().getSellVolume()).isEqualTo(300L);
        assertThat(found.getIndividual().getNetBuyVolume()).isEqualTo(-200L);
        assertThat(found.getForeigner().getBuyVolume()).isEqualTo(500L);
        assertThat(found.getInstitution().getSellVolume()).isEqualTo(200L);
        assertThat(found.getOtherCorporation().getBuyVolume()).isEqualTo(10L);
        assertThat(found.getForeignerHoldingQuantity()).isEqualTo(1_000_000L);
        assertThat(found.getForeignerHoldingRate()).isEqualTo(0.5089);
        assertThat(found.isConfirmed()).isTrue();
    }

    @Test
    @DisplayName("[잠정 기록의 개인·기타법인은 0이 아니라 null로 읽힌다 - 확정 여부 판단의 근거]")
    void saveAndFind_provisionalRecord_readsNullGroupsAsNull() {
        repository.saveAndFlush(provisional(LocalDate.of(2026, 10, 23)));

        StockInvestorTrading found = repository.findByStockCodeOrderByTradeDateDesc(STOCK, PageRequest.of(0, 1)).get(0);

        assertThat(found.getIndividual()).isNull();
        assertThat(found.getOtherCorporation()).isNull();
        assertThat(found.getForeignerHoldingRate()).isNull();
        assertThat(found.getForeigner().getNetBuyVolume()).isEqualTo(100L);
        assertThat(found.isConfirmed()).isFalse();
    }

    @Test
    @DisplayName("[잠정 기록을 확정치로 덮어쓰면 같은 행이 갱신되고 확정 여부가 바뀐다]")
    void updateFrom_overwritesProvisionalWithConfirmed() {
        StockInvestorTrading saved = repository.saveAndFlush(provisional(LocalDate.of(2026, 10, 23)));

        saved.updateFrom(LocalDateTime.of(2026, 10, 23, 18, 5), InvestorVolume.of(100, 300, -200),
            InvestorVolume.of(500, 400, 100), InvestorVolume.of(300, 200, 100), InvestorVolume.of(10, 10, 0),
            1_000_000L, 0.5089);
        repository.saveAndFlush(saved);

        assertThat(repository.countByStockCode(STOCK)).isEqualTo(1);
        StockInvestorTrading found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.isConfirmed()).isTrue();
        assertThat(found.getSourceUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 18, 5));
    }

    @Test
    @DisplayName("[같은 종목·거래일 행은 유니크 제약으로 중복 저장되지 않는다]")
    void save_sameStockAndDate_violatesUniqueConstraint() {
        repository.saveAndFlush(confirmed(LocalDate.of(2026, 10, 23)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> repository.saveAndFlush(confirmed(LocalDate.of(2026, 10, 23))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("[최신 N건은 거래일 내림차순이고 전체 최근 거래일은 종목과 무관하게 계산된다]")
    void findLatest_ordersByTradeDateDescending_andLatestTradeDateAcrossStocks() {
        repository.save(confirmed(LocalDate.of(2026, 10, 21)));
        repository.save(confirmed(LocalDate.of(2026, 10, 22)));
        StockInvestorTrading other = StockInvestorTrading.of("000660", LocalDate.of(2026, 10, 23),
            LocalDateTime.of(2026, 10, 23, 18, 5), null, InvestorVolume.of(1, 1, 0), InvestorVolume.of(1, 1, 0),
            null, null, null);
        repository.saveAndFlush(other);

        List<StockInvestorTrading> latest = repository.findByStockCodeOrderByTradeDateDesc(STOCK, PageRequest.of(0, 2));

        assertThat(latest).extracting(StockInvestorTrading::getTradeDate)
            .containsExactly(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 21));
        assertThat(repository.findLatestTradeDate()).contains(LocalDate.of(2026, 10, 23));
        assertThat(ReflectionTestUtils.getField(latest.get(0), "stockCode")).isEqualTo(STOCK);
    }
}
