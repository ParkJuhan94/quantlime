package com.quantlime.event.market;

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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    @DisplayName("[onDlt는 정상 케이스에서 배치 카운터를 감소시키고 dltNotifier.notify를 호출한다]")
    void onDlt_happyPath_completesCounterAndNotifiesDlt() {
        // given
        PriceRefreshRequestedMessage message = messageOf("005930");

        // when
        priceRefreshConsumer.onDlt(message);

        // then
        verify(priceRefreshBatchGate).completeOne(eq("run-1"), eq(PeerGroup.DOMESTIC));
        verify(dltNotifier).notify(eq("market-price-refresh"), eq(MarketTopics.PRICE_REFRESH_REQUESTED), anyString());
    }

    @Test
    @DisplayName("[onDlt는 completeOne이 Redis 장애로 던져도 예외를 전파하지 않는다 - 클래스 주석이 명시한 시나리오]")
    void onDlt_batchGateThrows_doesNotPropagate() {
        // given
        PriceRefreshRequestedMessage message = messageOf("005930");
        willThrow(new RuntimeException("Redis 장애")).given(priceRefreshBatchGate).completeOne(any(), any());

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
