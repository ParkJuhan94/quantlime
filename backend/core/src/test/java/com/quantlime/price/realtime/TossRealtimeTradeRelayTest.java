package com.quantlime.price.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.infra.toss.TossTokenManager;
import com.quantlime.infra.toss.realtime.TossRealtimeClient;
import com.quantlime.infra.toss.realtime.TossRealtimeHandshakeException;
import com.quantlime.infra.toss.realtime.TossRealtimeListener;
import com.quantlime.infra.toss.realtime.TossRealtimeMarket;
import com.quantlime.infra.toss.realtime.TossRealtimeSession;
import com.quantlime.infra.toss.realtime.TossRealtimeTrade;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.OverseasMarketCalendarCache;
import com.quantlime.price.cache.PreviousCloseCache;
import com.quantlime.price.dto.response.PriceSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TossRealtimeTradeRelayTest {

    private static final Map<TossRealtimeMarket, List<String>> TARGETS =
        Map.of(TossRealtimeMarket.KR, List.of("005930"));

    @Mock private TossRealtimeClient client;
    @Mock private TossRealtimeSession session;
    @Mock private TossTokenManager tokenManager;
    @Mock private RankingRealtimeTargetResolver resolver;
    @Mock private PriceRelayLeaderGate leaderGate;
    @Mock private PriceTopicSubscriptionTracker tracker;
    @Mock private DomesticMarketCalendarCache domesticCalendar;
    @Mock private OverseasMarketCalendarCache overseasCalendar;
    @Mock private PreviousCloseCache domesticPreviousClose;
    @Mock private PreviousCloseCache overseasPreviousClose;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T01:00:00Z"));
    private TossRealtimeTradeRelay relay;

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        relay = new TossRealtimeTradeRelay(client, tokenManager, resolver, leaderGate, tracker, domesticCalendar,
            overseasCalendar, domesticPreviousClose, overseasPreviousClose, messagingTemplate,
            new SimpleMeterRegistry(), clock);
        lenient().when(leaderGate.isLeader()).thenReturn(true);
        lenient().when(resolver.resolve()).thenReturn(TARGETS);
        lenient().when(tokenManager.getAccessToken()).thenReturn("token-1");
        lenient().when(client.connect(anyString(), any())).thenReturn(session);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(tracker.hasSubscribers(anyString())).thenReturn(true);
        lenient().when(domesticCalendar.isMarketOpenNow()).thenReturn(true);
        lenient().when(domesticPreviousClose.get(anyList())).thenReturn(Map.of("005930", 100_000.0));
    }

    private TossRealtimeListener connectedListener() {
        relay.sync();
        ArgumentCaptor<TossRealtimeListener> captor = ArgumentCaptor.forClass(TossRealtimeListener.class);
        verify(client).connect(anyString(), captor.capture());
        return captor.getValue();
    }

    private TossRealtimeTrade trade(double price, double volume, Instant at) {
        return new TossRealtimeTrade(TossRealtimeMarket.KR, "005930", price, volume,
            OffsetDateTime.ofInstant(at, ZoneOffset.ofHours(9)));
    }

    @Test
    @DisplayName("[리더가 아니면 접속하지 않는다 - 계정당 연결 한도를 서로 밀어내지 않게]")
    void followerDoesNotConnect() {
        // given
        given(leaderGate.isLeader()).willReturn(false);

        // when
        relay.sync();

        // then
        verify(client, never()).connect(anyString(), any());
    }

    @Test
    @DisplayName("[리더 지위를 잃으면 연결을 닫는다]")
    void closesWhenLeadershipLost() {
        // given
        connectedListener();
        given(leaderGate.isLeader()).willReturn(false);

        // when
        relay.sync();

        // then
        verify(session).close();
    }

    @Test
    @DisplayName("[구독할 종목이 없으면(양쪽 장 마감) 접속하지 않는다]")
    void doesNotConnectWithoutTargets() {
        // given
        given(resolver.resolve()).willReturn(Map.of());

        // when
        relay.sync();

        // then
        verify(client, never()).connect(anyString(), any());
    }

    @Test
    @DisplayName("[대상이 바뀔 때만 구독을 다시 선언한다 - 선언은 초당 5회로 제한된다]")
    void declaresOnlyWhenTargetsChange() {
        // given
        Map<TossRealtimeMarket, List<String>> changed = Map.of(TossRealtimeMarket.KR, List.of("000660"));

        // when
        relay.sync();
        relay.sync();
        given(resolver.resolve()).willReturn(changed);
        relay.sync();

        // then
        verify(session, times(1)).declareTrades(TARGETS);
        verify(session, times(1)).declareTrades(changed);
    }

    @Test
    @DisplayName("[거부된 종목은 다음 선언에서 뺀다 - 재선언해도 같은 이유로 거부된다]")
    void excludesRejectedTargets() {
        // given
        TossRealtimeListener listener = connectedListener();

        // when
        listener.onSubscriptions(0, List.of("trade:kr:005930"));
        given(resolver.resolve()).willReturn(Map.of(TossRealtimeMarket.KR, List.of("005930", "000660")));
        relay.sync();

        // then
        verify(session).declareTrades(Map.of(TossRealtimeMarket.KR, List.of("000660")));
    }

    @Test
    @DisplayName("[여러 체결이 몰려도 종목별 최신값 하나만 등락률과 함께 발행한다]")
    void coalescesLatestTradePerSymbol() {
        // given
        TossRealtimeListener listener = connectedListener();
        listener.onTrade(trade(101_000, 5, clock.instant()));
        listener.onTrade(trade(102_000, 5, clock.instant()));

        // when
        relay.flush();
        relay.flush();

        // then
        ArgumentCaptor<PriceSnapshot> captor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/price.005930"), captor.capture());
        assertThat(captor.getValue().currentPrice()).isEqualTo(102_000.0);
        assertThat(captor.getValue().changeRate()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("[구독 직후 오는 오래된 volume=0 프레임은 버린다 - 운영 프로브에서 실제로 왔다]")
    void dropsStaleFrames() {
        // given
        TossRealtimeListener listener = connectedListener();

        // when
        listener.onTrade(trade(101_000, 0, clock.instant()));
        listener.onTrade(trade(101_000, 5, clock.instant().minus(Duration.ofMinutes(4))));
        relay.flush();

        // then
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("[장이 닫혔거나 구독자가 없는 종목은 발행하지 않는다]")
    void skipsClosedMarketOrNoSubscribers() {
        // given
        TossRealtimeListener listener = connectedListener();
        listener.onTrade(trade(101_000, 5, clock.instant()));
        given(domesticCalendar.isMarketOpenNow()).willReturn(false);

        // when
        relay.flush();

        // then
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("[연결이 끊기면 백오프가 지난 뒤에만 다시 접속한다 - 다른 쪽 연결을 밀어내는 반복을 막는다]")
    void reconnectsAfterBackoff() {
        // given
        TossRealtimeListener listener = connectedListener();
        listener.onClosed("error");

        // when
        relay.sync();

        // then
        verify(client, times(1)).connect(anyString(), any());

        // when
        clock.advance(Duration.ofSeconds(61));
        relay.sync();

        // then
        verify(client, times(2)).connect(anyString(), any());
    }

    @Test
    @DisplayName("[401이면 그 토큰을 지우고 30초 뒤에 재시도한다]")
    void invalidatesTokenOnUnauthorized() {
        // given
        given(client.connect(anyString(), any())).willThrow(new TossRealtimeHandshakeException(401, null));

        // when
        relay.sync();
        relay.sync();

        // then
        verify(tokenManager, times(1)).invalidateToken();
        verify(client, times(1)).connect(anyString(), any());
    }

    @Test
    @DisplayName("[auth-revoked 뒤 Redis에 이미 새 토큰이 있으면 지우지 않고 새 토큰으로 접속한다]")
    void reusesTokenAlreadyRefreshed() {
        // given
        TossRealtimeListener listener = connectedListener();
        listener.onError("auth-revoked", "x");
        listener.onClosed("close");
        given(tokenManager.getAccessToken()).willReturn("token-2");

        // when
        clock.advance(Duration.ofSeconds(31));
        relay.sync();

        // then
        verify(tokenManager, never()).invalidateToken();
        verify(client).connect(eq("token-2"), any());
    }

    @Test
    @DisplayName("[auth-revoked 뒤 Redis 토큰이 끊긴 토큰과 같으면 지운다]")
    void invalidatesRevokedToken() {
        // given
        TossRealtimeListener listener = connectedListener();
        listener.onError("auth-revoked", "x");
        listener.onClosed("close");

        // when
        clock.advance(Duration.ofSeconds(31));
        relay.sync();

        // then
        verify(tokenManager).invalidateToken();
    }

    @Test
    @DisplayName("[60초마다 PING을 보내고 pong이 오래 없으면 재연결한다]")
    void pingsAndDetectsDeadConnection() {
        // given
        connectedListener();

        // when
        clock.advance(Duration.ofSeconds(61));
        relay.sync();

        // then
        verify(session).ping();

        // when
        clock.advance(Duration.ofSeconds(120));
        relay.sync();

        // then
        verify(session).close();
    }
}
