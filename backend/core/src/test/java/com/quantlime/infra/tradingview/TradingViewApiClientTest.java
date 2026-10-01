package com.quantlime.infra.tradingview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.tradingview.dto.TradingViewSymbolResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class TradingViewApiClientTest {

    private static final String BASE_URL = "https://tv.test";

    private MockRestServiceServer mockServer;
    private TradingViewApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TradingViewApiClient(builder.build());
    }

    @Test
    @DisplayName("[symbol과 fields=close,change 쿼리로 호출하고 change를 changeRate로 매핑한다]")
    void getSymbolQuote_mapsChangeToChangeRate() {
        // given
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/symbol")))
            .andExpect(queryParam("symbol", "TVC:US10Y"))
            .andExpect(queryParam("fields", "close,change"))
            .andRespond(withSuccess("{\"close\":4.21,\"change\":-0.35,\"extra\":1}", MediaType.APPLICATION_JSON));

        // when
        TradingViewSymbolResponse response = client.getSymbolQuote("TVC:US10Y");

        // then
        assertThat(response.close()).isEqualTo(4.21);
        assertThat(response.changeRate()).isEqualTo(-0.35);
        mockServer.verify();
    }

    @Test
    @DisplayName("[HTTP 오류는 ExternalApiException으로 감싸 호출측(MarketIndexCache)이 stale-serve하게 한다]")
    void getSymbolQuote_httpError_wraps() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/symbol")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.getSymbolQuote("TVC:US10Y")).isInstanceOf(ExternalApiException.class);
    }
}
