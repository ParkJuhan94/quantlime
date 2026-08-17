package com.quantlime.price.repository;

import com.quantlime.price.domain.DomesticRegularClosePrice;
import com.quantlime.support.DataJpaTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class DomesticRegularClosePriceQueryRepositoryImplTest extends DataJpaTestSupport {

    private static final String STOCK_CODE = "005930";

    @Autowired
    private DomesticRegularClosePriceRepository domesticRegularClosePriceRepository;

    @Test
    @DisplayName("[당일 행이 이미 저장돼 있어도 전일 정규장 종가는 그 이전 최신 값이다]")
    void findLatestBeforeDate_todayAlreadyCaptured_returnsPreviousTradingDayClose() {
        // given
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        domesticRegularClosePriceRepository.save(regularClose(yesterday, 70000L));
        domesticRegularClosePriceRepository.save(regularClose(today, 71500L));

        // when
        List<DomesticRegularClosePrice> result = domesticRegularClosePriceRepository.findLatestBeforeDate(
            List.of(STOCK_CODE), today);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTradeDate()).isEqualTo(yesterday);
        assertThat(result.get(0).getClosePrice()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("[캡처된 행이 없으면 빈 리스트를 반환한다]")
    void findLatestBeforeDate_noCapturedRow_returnsEmpty() {
        // when
        List<DomesticRegularClosePrice> result = domesticRegularClosePriceRepository.findLatestBeforeDate(
            List.of(STOCK_CODE), LocalDate.now());

        // then
        assertThat(result).isEmpty();
    }

    private DomesticRegularClosePrice regularClose(LocalDate tradeDate, long closePrice) {
        return DomesticRegularClosePrice.of(STOCK_CODE, tradeDate, closePrice);
    }
}
