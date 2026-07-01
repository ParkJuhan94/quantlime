package com.quantlab.infra.toss;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.infra.toss.dto.TossCandleResponse;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.infra.toss.exception.TossApiErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossApiClient {

    private final RestClient tossRestClient;
    private final TossTokenManager tokenManager;

    public TossCandleResponse getDailyCandles(String symbol, int count, String before) {
        try {
            String token = tokenManager.getAccessToken();

            TossCandleResponse response = tossRestClient.get()
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
                .body(TossCandleResponse.class);

            if (response == null) {
                throw new ExternalApiException(TossApiErrorCode.CANDLE_INQUIRY_FAILED);
            }

            return response;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(TossApiErrorCode.CANDLE_INQUIRY_FAILED, e);
        }
    }

    public TossPriceResponse getCurrentPrices(String symbols) {
        try {
            String token = tokenManager.getAccessToken();

            TossPriceResponse response = tossRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/prices")
                    .queryParam("symbols", symbols)
                    .build())
                .header("authorization", "Bearer " + token)
                .retrieve()
                .body(TossPriceResponse.class);

            if (response == null) {
                throw new ExternalApiException(TossApiErrorCode.PRICE_INQUIRY_FAILED);
            }

            return response;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(TossApiErrorCode.PRICE_INQUIRY_FAILED, e);
        }
    }
}
