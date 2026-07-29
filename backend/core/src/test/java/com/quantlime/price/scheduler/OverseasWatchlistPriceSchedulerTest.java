package com.quantlime.price.scheduler;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossPriceResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import com.quantlime.price.cache.OverseasPreviousCloseCache;
import com.quantlime.price.cache.OverseasWatchlistedStockCodeCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.cache.UsMarketCalendarCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OverseasWatchlistPriceSchedulerTest {

    private static final String STOCK_CODE = "AAPL";

    @Mock
    private UsMarketCalendarCache usMarketCalendarCache;

    @Mock
    private OverseasWatchlistedStockCodeCache overseasWatchlistedStockCodeCache;

    @Mock
    private OverseasPreviousCloseCache overseasPreviousCloseCache;

    @Mock
    private TossApiClient tossApiClient;

    @Mock
    private PriceCacheStore priceCacheStore;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private OverseasWatchlistPriceScheduler overseasWatchlistPriceScheduler;

    @Test
    @DisplayName("[미국장이 닫혀 있으면 관심종목 조회도 하지 않고 스킵한다]")
    void refresh_marketClosed_skipsEntirely() {
        // given
        given(usMarketCalendarCache.isMarketOpenNow()).willReturn(false);

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        verify(overseasWatchlistedStockCodeCache, never()).get();
        verify(tossApiClient, never()).getCurrentPrices(anyString());
    }

    @Test
    @DisplayName("[해외 관심종목이 없으면 Toss를 호출하지 않는다]")
    void refresh_emptyWatchlist_skipsTossCall() {
        // given
        given(usMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of());

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        verify(tossApiClient, never()).getCurrentPrices(anyString());
    }

    @Test
    @DisplayName("[Toss 현재가 조회 성공 시 전일종가 대비 등락률을 계산해 캐시에 저장하고 브로드캐스트한다]")
    void refresh_success_savesAndBroadcastsWithChangeRate() {
        // given: 340 -> 341.43은 약 +0.42%
        given(usMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 340.0));
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(
            new TossPriceResponse(List.of(
                new TossPriceResponse.TossPrice(STOCK_CODE, "2026-07-29T17:43:12+09:00", "341.43", "USD"))));

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        ArgumentCaptor<PriceSnapshot> savedCaptor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(priceCacheStore).save(savedCaptor.capture());
        PriceSnapshot saved = savedCaptor.getValue();
        assertThat(saved.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(saved.currentPrice()).isCloseTo(341.43, offset(0.001));
        assertThat(saved.changeRate()).isCloseTo(0.4206, offset(0.001));
        verify(messagingTemplate).convertAndSend(eq("/topic/price/" + STOCK_CODE), eq(saved));
    }

    @Test
    @DisplayName("[전일종가가 없으면 등락률 null로 저장하되 현재가는 그대로 캐시한다]")
    void refresh_noPreviousClose_savesWithNullChangeRate() {
        // given
        given(usMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of());
        given(tossApiClient.getCurrentPrices(STOCK_CODE)).willReturn(
            new TossPriceResponse(List.of(
                new TossPriceResponse.TossPrice(STOCK_CODE, "2026-07-29T17:43:12+09:00", "341.43", "USD"))));

        // when
        overseasWatchlistPriceScheduler.refreshAndBroadcast();

        // then
        ArgumentCaptor<PriceSnapshot> savedCaptor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(priceCacheStore).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().changeRate()).isNull();
    }

    @Test
    @DisplayName("[Toss 조회가 실패해도 예외가 전파되지 않는다(다음 틱에 재시도)]")
    void refresh_tossCallFails_doesNotPropagate() {
        // given
        given(usMarketCalendarCache.isMarketOpenNow()).willReturn(true);
        given(overseasWatchlistedStockCodeCache.get()).willReturn(List.of(STOCK_CODE));
        given(overseasPreviousCloseCache.get(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, 340.0));
        given(tossApiClient.getCurrentPrices(STOCK_CODE))
            .willThrow(new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED));

        // when & then
        assertThatCode(() -> overseasWatchlistPriceScheduler.refreshAndBroadcast())
            .doesNotThrowAnyException();
        verify(priceCacheStore, never()).save(any());
    }
}
