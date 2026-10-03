package com.quantlime.market.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import com.quantlime.stock.service.StockMasterService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceRefreshFailureHandlerTest {

    private static final String DOMESTIC_CODE = "005930";
    private static final String OVERSEAS_CODE = "AAPL";

    @Mock
    private StockMasterService stockMasterService;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private PriceRefreshFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PriceRefreshFailureHandler(stockMasterService, meterRegistry);
    }

    private static ExternalApiException candleFailure(HttpStatus status) {
        HttpClientErrorException cause = HttpClientErrorException.create(
            status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], null);
        return new ExternalApiException(TossApiErrorCode.CANDLE_INQUIRY_FAILED, cause);
    }

    private double failureCount(String peerGroup) {
        return meterRegistry.get("market.price.refresh.failures").tag("peerGroup", peerGroup).counter().count();
    }

    @Test
    @DisplayName("[국내 종목이 Toss stock-not-found(404)를 내면 가격 미커버로 표시해 이후 대상에서 제외한다]")
    void handleDomestic_stockNotFound_marksPriceUnsupported() {
        handler.handleDomestic(DOMESTIC_CODE, candleFailure(HttpStatus.NOT_FOUND));

        verify(stockMasterService).markPriceUnsupported(DOMESTIC_CODE);
        // 404는 "미커버 종목 표시"이지 장애가 아니라 실패 카운터에 세지 않는다.
        assertThat(meterRegistry.find("market.price.refresh.failures").counter()).isNull();
    }

    @Test
    @DisplayName("[국내 종목이 404가 아닌 실패를 내면 미커버로 표시하지 않는다(다음 기동 재시도)]")
    void handleDomestic_non404Failure_doesNotMark() {
        handler.handleDomestic(DOMESTIC_CODE, new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED));

        verify(stockMasterService, never()).markPriceUnsupported(any());
        // 이 실패는 삼켜져 재시도/DLT로 가지 않으므로 규모를 볼 수 있는 유일한 지표다.
        assertThat(failureCount("domestic")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[국내는 400(심볼 형식 미지원)을 미커버로 보지 않는다 - 400 판별은 해외 전용이다]")
    void handleDomestic_badRequest_doesNotMark() {
        handler.handleDomestic(DOMESTIC_CODE, candleFailure(HttpStatus.BAD_REQUEST));

        verify(stockMasterService, never()).markPriceUnsupported(any());
        assertThat(failureCount("domestic")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[해외 종목이 Toss stock-not-found(404)를 내면 가격 미커버로 표시해 이후 대상에서 제외한다]")
    void handleOverseas_stockNotFound_marksPriceUnsupported() {
        handler.handleOverseas(OVERSEAS_CODE, candleFailure(HttpStatus.NOT_FOUND));

        verify(stockMasterService).markPriceUnsupported(OVERSEAS_CODE);
        assertThat(meterRegistry.find("market.price.refresh.failures").counter()).isNull();
    }

    @Test
    @DisplayName("[해외 종목이 Toss 400(심볼 형식 미지원)을 내면 가격 미커버로 표시한다 - "
        + "\"AAC/UN\" 같은 \"/\" 포함 심볼이 404가 아닌 400으로 거부되며 실제로 겪은 무한 반복 버그]")
    void handleOverseas_unsupportedSymbolFormat_marksPriceUnsupported() {
        handler.handleOverseas("AAC/UN", candleFailure(HttpStatus.BAD_REQUEST));

        verify(stockMasterService).markPriceUnsupported("AAC/UN");
    }

    @Test
    @DisplayName("[해외 종목이 404가 아닌 실패를 내면 미커버로 표시하지 않는다(다음 기동 재시도) - "
        + "이 안전장치가 없으면 레이트리밋 실패가 매 스윕마다 계속 반복돼 다른 종목의 예산까지 갉아먹는다]")
    void handleOverseas_non404Failure_doesNotMark() {
        handler.handleOverseas(OVERSEAS_CODE, new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED));

        verify(stockMasterService, never()).markPriceUnsupported(any());
        assertThat(failureCount("overseas")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[ExternalApiException이 아닌 예외는 404 원인을 달고 있어도 미커버로 표시하지 않는다]")
    void handleDomestic_nonExternalApiException_doesNotMark() {
        handler.handleDomestic(DOMESTIC_CODE, new RuntimeException("boom"));

        verify(stockMasterService, never()).markPriceUnsupported(any());
        assertThat(failureCount("domestic")).isEqualTo(1.0);
    }
}
