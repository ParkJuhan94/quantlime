package com.quantlime.infra.upbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.upbit.dto.UpbitMinuteCandle;
import com.quantlime.infra.upbit.dto.UpbitTicker;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class UpbitApiClientTest {

    private static final String BASE_URL = "https://upbit.test";

    private MockRestServiceServer mockServer;
    private UpbitApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new UpbitApiClient(builder.build());
    }

    @Test
    @DisplayName("[티커 조회는 markets 쿼리로 호출하고 snake_case 응답을 매핑하며 모르는 필드는 무시한다]")
    void getTicker_mapsSnakeCaseAndIgnoresUnknownFields() {
        // given
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/v1/ticker")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("markets", "KRW-BTC"))
            .andRespond(withSuccess("""
                [{"market":"KRW-BTC","trade_price":95000000,"signed_change_rate":0.0123,"acc_trade_volume":1.5}]
                """, MediaType.APPLICATION_JSON));

        // when
        List<UpbitTicker> tickers = client.getTicker("KRW-BTC");

        // then
        assertThat(tickers).hasSize(1);
        assertThat(tickers.get(0).market()).isEqualTo("KRW-BTC");
        assertThat(tickers.get(0).tradePrice()).isEqualTo(95_000_000L);
        assertThat(tickers.get(0).signedChangeRate()).isEqualTo(0.0123);
        mockServer.verify();
    }

    @Test
    @DisplayName("[분봉 조회는 unit을 경로에, market/count를 쿼리에 실어 호출한다]")
    void getMinuteCandles_buildsPathAndQuery() {
        // given
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/v1/candles/minutes/30")))
            .andExpect(queryParam("market", "KRW-BTC"))
            .andExpect(queryParam("count", "48"))
            .andRespond(withSuccess("""
                [{"candle_date_time_kst":"2026-09-30T12:00:00","trade_price":95000000.5,"opening_price":1}]
                """, MediaType.APPLICATION_JSON));

        // when
        List<UpbitMinuteCandle> candles = client.getMinuteCandles("KRW-BTC", 30, 48);

        // then
        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).candleDateTimeKst()).isEqualTo("2026-09-30T12:00:00");
        assertThat(candles.get(0).tradePrice()).isEqualTo(95_000_000.5);
        mockServer.verify();
    }

    @Test
    @DisplayName("[서버 오류는 도메인 에러코드(UPBIT_000)의 ExternalApiException으로 감싼다]")
    void getTicker_serverError_wrapsAsExternalApiException() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/v1/ticker"))).andRespond(withServerError());

        assertThatThrownBy(() -> client.getTicker("KRW-BTC"))
            .isInstanceOfSatisfying(ExternalApiException.class, e -> assertThat(e.getCode()).isEqualTo("UPBIT_000"));
    }

    @Test
    @DisplayName("[빈 본문(null)이면 캔들 조회 에러코드(UPBIT_001)로 실패한다]")
    void getMinuteCandles_emptyBody_throwsCandleError() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/v1/candles/minutes/30")))
            .andRespond(withSuccess());

        assertThatThrownBy(() -> client.getMinuteCandles("KRW-BTC", 30, 48))
            .isInstanceOfSatisfying(ExternalApiException.class, e -> assertThat(e.getCode()).isEqualTo("UPBIT_001"));
    }
}
