package com.quantlime.notification.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.market.event.ScoreBatchCompletedEvent;
import com.quantlime.notification.event.QuadrantAlertRequestedEvent;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.implement.SubscriptionReader;
import com.quantlime.watchlist.implement.WatchlistGroupReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class QuadrantAlertDispatcherTest {

    @Mock
    private WatchlistGroupReader watchlistGroupReader;
    @Mock
    private SubscriptionReader subscriptionReader;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private QuadrantAlertDispatcher dispatcher;

    @Test
    @DisplayName("[옵트인한 사용자가 없으면 구독 조회도 이벤트 발행도 하지 않는다]")
    void noOptedInUsers_doesNothing() {
        given(watchlistGroupReader.findUserIdsWithQuadrantAlertEnabled()).willReturn(List.of());

        dispatcher.onScoreBatchCompleted(new ScoreBatchCompletedEvent());

        verifyNoInteractions(subscriptionReader, eventPublisher);
    }

    @Test
    @DisplayName("[옵트인했어도 구독이 끊긴 사용자는 제외하고 구독자마다 이벤트를 하나씩 발행한다]")
    void publishesOnlyForActiveSubscribers() {
        given(watchlistGroupReader.findUserIdsWithQuadrantAlertEnabled()).willReturn(List.of(1L, 2L, 3L));
        given(subscriptionReader.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE)).willReturn(List.of(1L, 3L, 9L));

        dispatcher.onScoreBatchCompleted(new ScoreBatchCompletedEvent());

        verify(eventPublisher).publishEvent(new QuadrantAlertRequestedEvent(1L));
        verify(eventPublisher).publishEvent(new QuadrantAlertRequestedEvent(3L));
        verify(eventPublisher, never()).publishEvent(new QuadrantAlertRequestedEvent(2L));
        verify(eventPublisher, times(2)).publishEvent(org.mockito.ArgumentMatchers.any(QuadrantAlertRequestedEvent.class));
    }
}
