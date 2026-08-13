package com.quantlime.price.dto.mapper;

import com.quantlime.price.DomesticDailyPriceFixture;
import com.quantlime.price.OverseasDailyPriceFixture;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
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
        // given: DomesticDailyPriceFixture의 종가는 105L 고정값
        DomesticDailyPrice latestClose = DomesticDailyPriceFixture.createDailyPrice("005930", LocalDate.of(2026, 7, 16));

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse(latestClose, 100.0);

        // then
        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.price()).isEqualTo(105L);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.timestamp()).isEqualTo("2026-07-16");
    }

    @Test
    @DisplayName("[전일종가가 있으면 등락률을 계산한다]")
    void toCurrentPriceResponse_withPreviousClose_calculatesChangeRate() {
        // given: DomesticDailyPriceFixture의 종가는 105L 고정값
        DomesticDailyPrice latestClose = DomesticDailyPriceFixture.createDailyPrice("005930", LocalDate.of(2026, 7, 16));

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse(latestClose, 100.0);

        // then
        assertThat(response.changeRate()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("[전일종가가 없으면(관심종목이 아니어서 캐시에 없는 경우 등) 등락률은 null이다]")
    void toCurrentPriceResponse_withoutPreviousClose_returnsNullChangeRate() {
        // given
        DomesticDailyPrice latestClose = DomesticDailyPriceFixture.createDailyPrice("005930", LocalDate.of(2026, 7, 16));

        // when
        CurrentPriceResponse response = PriceMapper.toCurrentPriceResponse(latestClose, null);

        // then
        assertThat(response.changeRate()).isNull();
    }

    @Test
    @DisplayName("[DomesticDailyPrice를 DailyChartResponse로 매핑한다]")
    void toDailyChartResponse_mapsAllFields() {
        // given
        LocalDate tradeDate = LocalDate.of(2026, 7, 3);
        DomesticDailyPrice domesticDailyPrice = DomesticDailyPriceFixture.createDailyPrice("005930", tradeDate);

        // when
        DailyChartResponse response = PriceMapper.toDailyChartResponse(domesticDailyPrice);

        // then: open/high/low/close는 Double로 확대된다(해외 종목 공용 응답 타입).
        assertThat(response.tradeDate()).isEqualTo(tradeDate);
        assertThat(response.open()).isEqualTo(domesticDailyPrice.getOpenPrice().doubleValue());
        assertThat(response.high()).isEqualTo(domesticDailyPrice.getHighPrice().doubleValue());
        assertThat(response.low()).isEqualTo(domesticDailyPrice.getLowPrice().doubleValue());
        assertThat(response.close()).isEqualTo(domesticDailyPrice.getClosePrice().doubleValue());
        assertThat(response.volume()).isEqualTo(domesticDailyPrice.getVolume());
    }

    @Test
    @DisplayName("[OverseasDailyPrice를 DailyChartResponse로 매핑한다]")
    void toDailyChartResponse_overseas_mapsAllFields() {
        // given
        LocalDate tradeDate = LocalDate.of(2026, 7, 3);
        OverseasDailyPrice overseasDailyPrice = OverseasDailyPriceFixture.createDailyPrice("AAPL", tradeDate);

        // when
        DailyChartResponse response = PriceMapper.toDailyChartResponse(overseasDailyPrice);

        // then
        assertThat(response.tradeDate()).isEqualTo(tradeDate);
        assertThat(response.open()).isEqualTo(overseasDailyPrice.getOpenPrice());
        assertThat(response.high()).isEqualTo(overseasDailyPrice.getHighPrice());
        assertThat(response.low()).isEqualTo(overseasDailyPrice.getLowPrice());
        assertThat(response.close()).isEqualTo(overseasDailyPrice.getClosePrice());
        assertThat(response.volume()).isEqualTo(overseasDailyPrice.getVolume());
    }
}
