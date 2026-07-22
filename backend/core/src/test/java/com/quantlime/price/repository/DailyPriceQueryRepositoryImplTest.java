package com.quantlime.price.repository;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.support.DataJpaTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class DailyPriceQueryRepositoryImplTest extends DataJpaTestSupport {

    private static final String STOCK_CODE = "005930";

    @Autowired
    private DailyPriceRepository dailyPriceRepository;

    @Test
    @DisplayName("[당일 행이 이미 저장돼 있어도 전일 종가는 그 이전 최신 값이다]")
    void findLatestBeforeDate_todayAlreadyCollected_returnsPreviousTradingDayClose() {
        // given: 장중 캐치업 수집으로 당일(오늘) 행이 이미 들어와 있는 상황을 재현
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        dailyPriceRepository.save(candle(yesterday, 70000L));
        dailyPriceRepository.save(candle(today, 71500L));

        // when
        List<DailyPrice> result = dailyPriceRepository.findLatestBeforeDate(
            List.of(STOCK_CODE), today);

        // then: 당일(71500)이 아니라 전일 종가(70000)를 반환해야 한다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTradeDate()).isEqualTo(yesterday);
        assertThat(result.get(0).getClosePrice()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("[당일 행이 없으면 가장 최근 저장된 행을 반환한다]")
    void findLatestBeforeDate_noTodayRow_returnsLatestStored() {
        // given
        LocalDate today = LocalDate.now();
        dailyPriceRepository.save(candle(today.minusDays(3), 68000L));
        dailyPriceRepository.save(candle(today.minusDays(1), 70000L));

        // when
        List<DailyPrice> result = dailyPriceRepository.findLatestBeforeDate(
            List.of(STOCK_CODE), today);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTradeDate()).isEqualTo(today.minusDays(1));
        assertThat(result.get(0).getClosePrice()).isEqualTo(70000L);
    }

    private DailyPrice candle(LocalDate tradeDate, long closePrice) {
        return DailyPrice.of(STOCK_CODE, tradeDate, closePrice, closePrice, closePrice, closePrice, 1000000L);
    }
}
