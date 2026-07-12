package com.quantlab.infra.upbit;

import com.quantlab.common.util.ExternalApiInvoker;
import com.quantlab.infra.upbit.dto.UpbitTicker;
import com.quantlab.infra.upbit.exception.UpbitApiErrorCode;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Upbit 공개 시세 API(인증 불필요). 토스증권 Open API가 암호화폐를 다루지
 * 않아 비트코인 위젯만을 위해 별도 연동한다.
 */
@Component
@RequiredArgsConstructor
public class UpbitApiClient {

    private final RestClient upbitRestClient;

    public List<UpbitTicker> getTicker(String market) {
        return ExternalApiInvoker.call(
            UpbitApiErrorCode.TICKER_INQUIRY_FAILED,
            () -> {
                UpbitTicker[] tickers = upbitRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/v1/ticker")
                        .queryParam("markets", market)
                        .build())
                    .retrieve()
                    .body(UpbitTicker[].class);
                return tickers == null ? null : Arrays.asList(tickers);
            });
    }
}
