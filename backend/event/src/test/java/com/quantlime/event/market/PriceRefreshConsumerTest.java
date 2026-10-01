package com.quantlime.event.market;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.event.observability.KafkaDltNotifier;
import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.market.service.PriceRefreshBatchGate;
import com.quantlime.score.domain.PeerGroup;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code @DltHandler}는 절대 예외를 던지면 안 된다는 불변식의 회귀 테스트.
 * 클래스 주석이 명시한 "completeOne이 Redis 장애 시 던질 수 있는" 시나리오를
 * 직접 재현한다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceRefreshConsumerTest {

    @Mock
    private MarketDataRefreshService marketDataRefreshService;

    @Mock
    private PriceRefreshBatchGate priceRefreshBatchGate;

    @Mock
    private KafkaDltNotifier dltNotifier;

    @InjectMocks
    private PriceRefreshConsumer priceRefreshConsumer;

    private PriceRefreshRequestedMessage messageOf(String stockCode) {
        return new PriceRefreshRequestedMessage(
            UUID.randomUUID(), Instant.now(), 1, "run-1", stockCode, "domestic", LocalDate.now());
    }

    @Test
    @DisplayName("[진행 중인 배치의 메시지는 종목 갱신 후 완료를 통지한다]")
    void onPriceRefreshRequested_activeBatch_refreshesAndCompletes() {
        // given
        PriceRefreshRequestedMessage message = messageOf("005930");
        given(priceRefreshBatchGate.isActive("run-1", PeerGroup.DOMESTIC)).willReturn(true);

        // when
        priceRefreshConsumer.onPriceRefreshRequested(message);

        // then
        verify(marketDataRefreshService).refreshSingleStockFromFanOut("005930", message.latestScoreDate());
        verify(priceRefreshBatchGate).completeOne("run-1", PeerGroup.DOMESTIC, "005930");
    }

    @Test
    @DisplayName("[만료/종료된 배치의 메시지는 Toss 호출 없이 건너뛴다 - 중단된 배치의 잔여 메시지 적체 대응]")
    void onPriceRefreshRequested_staleBatch_isSkipped() {
        // given
        given(priceRefreshBatchGate.isActive("run-1", PeerGroup.DOMESTIC)).willReturn(false);

        // when
        priceRefreshConsumer.onPriceRefreshRequested(messageOf("005930"));

        // then
        verifyNoInteractions(marketDataRefreshService);
        verify(priceRefreshBatchGate, never()).completeOne(any(), any(), any());
    }

    @Test
    @DisplayName("[갱신이 예외를 던지면 완료를 통지하지 않고 전파한다 - @RetryableTopic이 재시도하게 하려는 것]")
    void onPriceRefreshRequested_refreshFails_propagatesWithoutCompleting() {
        // given
        given(priceRefreshBatchGate.isActive("run-1", PeerGroup.DOMESTIC)).willReturn(true);
        willThrow(new IllegalStateException("quant-engine 실패"))
            .given(marketDataRefreshService).refreshSingleStockFromFanOut(any(), any());

        // when & then
        assertThatThrownBy(() -> priceRefreshConsumer.onPriceRefreshRequested(messageOf("005930")))
            .isInstanceOf(IllegalStateException.class);
        verify(priceRefreshBatchGate, never()).completeOne(any(), any(), any());
    }

    @Test
    @DisplayName("[onDlt는 정상 케이스에서 배치 완료를 통지하고 dltNotifier.notify를 호출한다]")
    void onDlt_happyPath_completesCounterAndNotifiesDlt() {
        // given
        PriceRefreshRequestedMessage message = messageOf("005930");

        // when
        priceRefreshConsumer.onDlt(message);

        // then
        verify(priceRefreshBatchGate).completeOne(eq("run-1"), eq(PeerGroup.DOMESTIC), eq("005930"));
        verify(dltNotifier).notify(eq("market-price-refresh"), eq(MarketTopics.PRICE_REFRESH_REQUESTED), anyString());
    }

    @Test
    @DisplayName("[onDlt는 completeOne이 Redis 장애로 던져도 예외를 전파하지 않는다 - 클래스 주석이 명시한 시나리오]")
    void onDlt_batchGateThrows_doesNotPropagate() {
        // given
        PriceRefreshRequestedMessage message = messageOf("005930");
        willThrow(new RuntimeException("Redis 장애")).given(priceRefreshBatchGate).completeOne(any(), any(), any());

        // when & then
        assertThatCode(() -> priceRefreshConsumer.onDlt(message)).doesNotThrowAnyException();
        // completeOne이 실패해 예외를 던졌으므로 뒤이은 notify는 호출되지 않는다(같은 try 블록에서 중단)
        verifyNoInteractions(dltNotifier);
    }

    @Test
    @DisplayName("[onDlt는 dltNotifier.notify 자체가 실패해도 예외를 전파하지 않는다]")
    void onDlt_dltNotifierThrows_doesNotPropagate() {
        // given
        PriceRefreshRequestedMessage message = messageOf("005930");
        willThrow(new RuntimeException("slack down")).given(dltNotifier).notify(any(), any(), any());

        // when & then
        assertThatCode(() -> priceRefreshConsumer.onDlt(message)).doesNotThrowAnyException();
    }
}
