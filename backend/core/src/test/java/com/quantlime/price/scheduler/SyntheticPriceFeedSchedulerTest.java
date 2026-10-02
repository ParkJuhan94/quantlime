package com.quantlime.price.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.WatchlistedStockCodeCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 로컬 scale-out 검증 전용 합성 시세 생성기다(운영 경로 아님). 그래도 테스트하는 이유는 두 가지다:
 * 활성화 조건이 {@code price-feed.mode=synthetic}으로만 좁혀져 있어야 운영에서 가짜 시세가 섞이지 않고,
 * 국내(저장만)/해외(저장 + 브로드캐스트) 비대칭 처리를 실수로 깨면 검증 도구 자체가 틀린 결과를 내기 때문이다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SyntheticPriceFeedSchedulerTest {

    // 틱당 최대 ±0.5% 랜덤워크(구현 상수와 같은 값) - 경계는 부동소수점 여유를 둔다
    private static final double MAX_STEP = 0.005 + 1e-9;

    @Mock
    private WatchlistedStockCodeCache domesticWatchlistedStockCodeCache;

    @Mock
    private WatchlistedStockCodeCache overseasWatchlistedStockCodeCache;

    @Mock
    private PreviousCloseCache domesticPreviousCloseCache;

    @Mock
    private PreviousCloseCache overseasPreviousCloseCache;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PriceRelayLeaderGate priceRelayLeaderGate;

    private SyntheticPriceFeedScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SyntheticPriceFeedScheduler(
            domesticWatchlistedStockCodeCache, overseasWatchlistedStockCodeCache,
            domesticPreviousCloseCache, overseasPreviousCloseCache,
            priceCacheStore, messagingTemplate, priceRelayLeaderGate);
    }

    private void leader(boolean isLeader) {
        given(priceRelayLeaderGate.isLeader()).willReturn(isLeader);
    }

    @SuppressWarnings("unchecked")
    private List<PriceSnapshot> savedSnapshots(int expectedCalls) {
        ArgumentCaptor<List<PriceSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(priceCacheStore, times(expectedCalls)).saveAll(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    @Test
    @DisplayName("[활성화 조건은 price-feed.mode=synthetic 하나뿐이다 - 기본값(toss)인 운영에는 빈이 만들어지지 않는다]")
    void activation_isLimitedToSyntheticMode() {
        ConditionalOnProperty condition = SyntheticPriceFeedScheduler.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition.name()).containsExactly("price-feed.mode");
        assertThat(condition.havingValue()).isEqualTo("synthetic");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test
    @DisplayName("[리더가 아니면 캐시 조회도 저장도 브로드캐스트도 하지 않는다 - 인스턴스가 여럿이어도 생산자는 하나]")
    void feedOnce_notLeader_doesNothing() {
        leader(false);

        scheduler.feedOnce();

        verifyNoInteractions(domesticWatchlistedStockCodeCache, overseasWatchlistedStockCodeCache,
            domesticPreviousCloseCache, overseasPreviousCloseCache, priceCacheStore, messagingTemplate);
    }

    @Test
    @DisplayName("[관심종목이 없으면 저장도 브로드캐스트도 하지 않는다]")
    void feedOnce_noWatchlistedStocks_doesNothing() {
        leader(true);
        given(domesticWatchlistedStockCodeCache.get()).willReturn(List.of());
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of());

        scheduler.feedOnce();

        verifyNoInteractions(priceCacheStore, messagingTemplate, domesticPreviousCloseCache, overseasPreviousCloseCache);
    }

    @Test
    @DisplayName("[국내는 전일종가에서 ±0.5% 안으로 시드한 시세를 저장만 하고 브로드캐스트는 하지 않는다(릴레이 스케줄러 몫)]")
    void feedOnce_domestic_savesOnly_seededFromPreviousClose() {
        leader(true);
        List<String> codes = List.of("005930", "000660");
        given(domesticWatchlistedStockCodeCache.get()).willReturn(codes);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of());
        given(domesticPreviousCloseCache.get(codes)).willReturn(Map.of("005930", 70_000.0, "000660", 120_000.0));

        scheduler.feedOnce();

        List<PriceSnapshot> saved = savedSnapshots(1);
        assertThat(saved).extracting(PriceSnapshot::stockCode).containsExactly("005930", "000660");
        assertThat(saved.get(0).currentPrice()).isBetween(70_000.0 * (1 - MAX_STEP), 70_000.0 * (1 + MAX_STEP));
        assertThat(saved.get(1).currentPrice()).isBetween(120_000.0 * (1 - MAX_STEP), 120_000.0 * (1 + MAX_STEP));
        assertThat(saved).allSatisfy(snapshot -> {
            assertThat(snapshot.changeRate()).isNotNull();
            assertThat(snapshot.timestamp()).isNotBlank();
        });
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("[전일종가가 없는 종목은 국내 기준가 50,000에서 시드한다]")
    void feedOnce_domesticWithoutPreviousClose_usesBasePrice() {
        leader(true);
        List<String> codes = List.of("111111");
        given(domesticWatchlistedStockCodeCache.get()).willReturn(codes);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of());
        given(domesticPreviousCloseCache.get(codes)).willReturn(Map.of());

        scheduler.feedOnce();

        PriceSnapshot snapshot = savedSnapshots(1).get(0);
        assertThat(snapshot.currentPrice()).isBetween(50_000.0 * (1 - MAX_STEP), 50_000.0 * (1 + MAX_STEP));
        assertThat(snapshot.changeRate()).isNull(); // 전일종가가 없으면 등락률도 계산하지 않는다
    }

    @Test
    @DisplayName("[해외는 저장 + 종목별 /topic/price.{코드}로 브로드캐스트까지 직접 수행하고, 기준가는 100이다]")
    void feedOnce_overseas_savesAndBroadcasts() {
        leader(true);
        List<String> codes = List.of("AAPL", "TSLA");
        given(domesticWatchlistedStockCodeCache.get()).willReturn(List.of());
        given(overseasWatchlistedStockCodeCache.get()).willReturn(codes);
        given(overseasPreviousCloseCache.get(codes)).willReturn(Map.of("AAPL", 190.0));

        scheduler.feedOnce();

        List<PriceSnapshot> saved = savedSnapshots(1);
        assertThat(saved).extracting(PriceSnapshot::stockCode).containsExactly("AAPL", "TSLA");
        assertThat(saved.get(0).currentPrice()).isBetween(190.0 * (1 - MAX_STEP), 190.0 * (1 + MAX_STEP));
        assertThat(saved.get(1).currentPrice()).isBetween(100.0 * (1 - MAX_STEP), 100.0 * (1 + MAX_STEP));
        verify(messagingTemplate).convertAndSend(eq("/topic/price.AAPL"), eq(saved.get(0)));
        verify(messagingTemplate).convertAndSend(eq("/topic/price.TSLA"), eq(saved.get(1)));
    }

    @Test
    @DisplayName("[다음 틱은 직전 합성가에서 이어서 ±0.5% 안으로만 움직인다(랜덤워크 연속성)]")
    void feedOnce_secondTick_continuesFromLastPrice() {
        leader(true);
        List<String> codes = List.of("005930");
        given(domesticWatchlistedStockCodeCache.get()).willReturn(codes);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of());
        given(domesticPreviousCloseCache.get(codes)).willReturn(Map.of("005930", 70_000.0));

        scheduler.feedOnce();
        double first = savedSnapshots(1).get(0).currentPrice();
        scheduler.feedOnce();
        double second = savedSnapshots(2).get(0).currentPrice();

        assertThat(second).isBetween(first * (1 - MAX_STEP), first * (1 + MAX_STEP));
    }

    @Test
    @DisplayName("[캐시/저장 중 예외가 나도 스케줄러 스레드로 전파하지 않는다]")
    void feedOnce_failure_isNotPropagated() {
        leader(true);
        given(domesticWatchlistedStockCodeCache.get()).willThrow(new RuntimeException("db down"));

        assertThatCode(() -> scheduler.feedOnce()).doesNotThrowAnyException();
        verify(priceCacheStore, never()).saveAll(anyList());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
