package com.quantlab.infra.toss;

import com.quantlab.common.util.ExternalApiInvoker;
import com.quantlab.infra.toss.dto.TossCandleResponse;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.infra.toss.exception.TossApiErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossApiClient {

    private final RestClient tossRestClient;
    private final TossTokenManager tokenManager;

    public TossCandleResponse getDailyCandles(String symbol, int count, String before) {
        String token = tokenManager.getAccessToken();
        return ExternalApiInvoker.call(
            TossApiErrorCode.CANDLE_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/api/v1/candles")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", "1d")
                        .queryParam("count", count)
                        .queryParam("adjusted", true);
                    if (before != null) {
                        builder.queryParam("before", before);
                    }
                    return builder.build();
                })
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossCandleResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossApiErrorCode.RATE_LIMIT_EXCEEDED);
    }

    public TossPriceResponse getCurrentPrices(String symbols) {
        String token = tokenManager.getAccessToken();
        return ExternalApiInvoker.call(
            TossApiErrorCode.PRICE_INQUIRY_FAILED,
            () -> tossRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/prices")
                    .queryParam("symbols", symbols)
                    .build())
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossPriceResponse.class),
            HttpClientErrorException.TooManyRequests.class,
            TossApiErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
