package com.quantlime.price.dto.mapper;

import com.quantlime.price.DailyPriceFixture;
import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.dto.response.CurrentPriceResponse;
import com.quantlime.price.dto.response.DailyChartResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PriceMapperTest {

    @Test
    @DisplayName("[DB의 마지막 종가를 그대로 가격으로 매핑한다]")
    void toCurrentPriceResponse_mapsLastCloseAsPrice() {
        // given: DailyPriceFixture의 종가는 105L 고정값
        DailyPrice latestClose = DailyPriceFixture.createDailyPrice("005930", LocalDate.of(2026, 7, 16));

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse(latestClose, 100L);

        // then
        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.price()).isEqualTo(105L);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.timestamp()).isEqualTo("2026-07-16");
    }

    @Test
    @DisplayName("[전일종가가 있으면 등락률을 계산한다]")
    void toCurrentPriceResponse_withPreviousClose_calculatesChangeRate() {
        // given: DailyPriceFixture의 종가는 105L 고정값
        DailyPrice latestClose = DailyPriceFixture.createDailyPrice("005930", LocalDate.of(2026, 7, 16));

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse(latestClose, 100L);

        // then
        assertThat(response.changeRate()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("[전일종가가 없으면(관심종목이 아니어서 캐시에 없는 경우 등) 등락률은 null이다]")
    void toCurrentPriceResponse_withoutPreviousClose_returnsNullChangeRate() {
        // given
        DailyPrice latestClose = DailyPriceFixture.createDailyPrice("005930", LocalDate.of(2026, 7, 16));

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse(latestClose, null);

        // then
        assertThat(response.changeRate()).isNull();
    }

    @Test
    @DisplayName("[DailyPrice를 DailyChartResponse로 매핑한다]")
    void toDailyChartResponse_mapsAllFields() {
        // given
        LocalDate tradeDate = LocalDate.of(2026, 7, 3);
        DailyPrice dailyPrice = DailyPriceFixture.createDailyPrice("005930", tradeDate);

        // when
        DailyChartResponse response = PriceMapper.toDailyChartResponse(dailyPrice);

        // then
        assertThat(response.tradeDate()).isEqualTo(tradeDate);
        assertThat(response.open()).isEqualTo(dailyPrice.getOpenPrice());
        assertThat(response.high()).isEqualTo(dailyPrice.getHighPrice());
        assertThat(response.low()).isEqualTo(dailyPrice.getLowPrice());
        assertThat(response.close()).isEqualTo(dailyPrice.getClosePrice());
        assertThat(response.volume()).isEqualTo(dailyPrice.getVolume());
    }
}
