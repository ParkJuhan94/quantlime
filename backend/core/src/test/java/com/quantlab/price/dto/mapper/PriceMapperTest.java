package com.quantlab.price.dto.mapper;

import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.price.DailyPriceFixture;
import com.quantlab.price.domain.DailyPrice;
import com.quantlab.price.dto.response.CurrentPriceResponse;
import com.quantlab.price.dto.response.DailyChartResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PriceMapperTest {

    @Test
    @DisplayName("[정상 lastPrice는 Long으로 파싱된다]")
    void toCurrentPriceResponse_validPrice_parsesLong() {
        // given
        TossPriceResponse.TossPrice tossPrice = new TossPriceResponse.TossPrice(
            "005930", "2026-07-06T09:00:00+09:00", "70000", "KRW");

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse("005930", tossPrice);

        // then
        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.price()).isEqualTo(70000L);
        assertThat(response.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("[lastPrice가 빈 문자열이면 price는 null이다]")
    void toCurrentPriceResponse_blankPrice_returnsNullPrice() {
        // given
        TossPriceResponse.TossPrice tossPrice = new TossPriceResponse.TossPrice(
            "005930", "2026-07-06T09:00:00+09:00", "", "KRW");

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse("005930", tossPrice);

        // then
        assertThat(response.price()).isNull();
    }

    @Test
    @DisplayName("[lastPrice가 null이면 price는 null이다]")
    void toCurrentPriceResponse_nullPrice_returnsNullPrice() {
        // given
        TossPriceResponse.TossPrice tossPrice = new TossPriceResponse.TossPrice(
            "005930", "2026-07-06T09:00:00+09:00", null, "KRW");

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse("005930", tossPrice);

        // then
        assertThat(response.price()).isNull();
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
