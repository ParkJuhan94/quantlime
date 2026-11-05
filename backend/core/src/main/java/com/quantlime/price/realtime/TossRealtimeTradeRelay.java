package com.quantlime.price.realtime;

import com.quantlime.common.lock.PriceRelayLeaderGate;
import com.quantlime.common.util.SafeExecutor;
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
import com.quantlime.price.util.ChangeRateCalculator;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토스 체결 웹소켓을 받아 랭킹 표 상위 행의 현재가를 STOMP로 내려보낸다. 순위·거래대금은 지금처럼
 * REST 폴링이 맡고 여기서는 현재가와 등락률만 다룬다. 프론트는 푸시가 끊기면 REST 값으로 돌아간다.
 *
 * <ul>
 *   <li><b>리더만 접속한다.</b> 계정당 연결이 2개라 인스턴스마다 접속하면 서로를 밀어낸다.
 *   <li><b>종목별 최신값만 묶어 보낸다.</b> 체결은 순간적으로 몰리고 유실돼도 최신값이 우선이다.
 *   <li><b>오래된 프레임을 버린다.</b> 스펙은 스냅샷이 없다지만 실제로는 구독 직후 몇 분 전 시각에
 *       {@code volume=0}인 프레임이 온다(운영 프로브, 2026-11-05).
 *   <li><b>재연결은 백오프를 지킨다.</b> 토큰은 계정당 1개라 재발급이 다른 쪽 연결을 끊는다. 짧은
 *       간격으로 재접속·재발급하면 서로 끊는 반복이 된다.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "toss-realtime.enabled", havingValue = "true")
public class TossRealtimeTradeRelay {

    private static final String PRICE_TOPIC_PREFIX = "/topic/price.";
    private static final String ERROR_AUTH_REVOKED = "auth-revoked";
    private static final String ERROR_RATE_LIMITED = "rate-limit-exceeded";
    private static final String METRIC_FRAMES = "toss.realtime.frames";
    private static final String METRIC_PUBLISHED = "toss.realtime.published";
    private static final String METRIC_CONNECTS = "toss.realtime.connects";

    private static final Duration MAX_TRADE_AGE = Duration.ofSeconds(60);
    private static final Duration PING_INTERVAL = Duration.ofSeconds(60);
    // 서버는 무발신 180초에 끊는다. 그 안에 pong이 안 오면 죽은 연결로 보고 다시 맺는다.
    private static final Duration PONG_TIMEOUT = Duration.ofSeconds(170);
    private static final Duration STABLE_SESSION = Duration.ofSeconds(60);
    private static final long MAX_BACKOFF_SECONDS = 60;
    private static final long AUTH_FAILURE_BACKOFF_SECONDS = 30;
    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_FORBIDDEN = 403;
    // 허용 IP 문제는 재시도로 풀리지 않는다.
    private static final long FORBIDDEN_BACKOFF_SECONDS = 300;

    private final TossRealtimeClient tossRealtimeClient;
    private final TossTokenManager tossTokenManager;
    private final RankingRealtimeTargetResolver targetResolver;
    private final PriceRelayLeaderGate priceRelayLeaderGate;
    private final PriceTopicSubscriptionTracker priceTopicSubscriptionTracker;
    private final DomesticMarketCalendarCache domesticMarketCalendarCache;
    private final OverseasMarketCalendarCache overseasMarketCalendarCache;
    // 필드명으로 PriceCacheConfig의 두 Bean을 구분한다.
    private final PreviousCloseCache domesticPreviousCloseCache;
    private final PreviousCloseCache overseasPreviousCloseCache;
    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private final Map<String, TossRealtimeTrade> pendingBySymbol = new ConcurrentHashMap<>();
    private final Set<String> rejectedTargets = ConcurrentHashMap.newKeySet();

    @Nullable
    private volatile Connection connection;
    // 연결이 끊긴 알림은 HTTP 클라이언트 스레드에서 오므로 Redis·토큰 호출은 sync로 미룬다.
    @Nullable
    private volatile String revokedToken;
    private volatile Instant nextConnectAt = Instant.MIN;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    @Scheduled(fixedDelayString = "${toss-realtime.sync-interval-ms:10000}")
    public void sync() {
        SafeExecutor.runSafely("토스 실시간 연결 동기화", this::syncOnce);
    }

    @Scheduled(fixedDelayString = "${toss-realtime.flush-interval-ms:1000}", scheduler = "priceSweepTaskScheduler")
    public void flush() {
        SafeExecutor.runSafely("토스 실시간 시세 발행", this::flushOnce);
    }

    @PreDestroy
    void shutdown() {
        closeConnection();
    }

    private void syncOnce() {
        if (!priceRelayLeaderGate.isLeader()) {
            closeConnection();
            return;
        }
        Map<TossRealtimeMarket, List<String>> targets = withoutRejected(targetResolver.resolve());
        if (targets.values().stream().allMatch(List::isEmpty)) {
            closeConnection();
            return;
        }
        Connection current = openConnection();
        if (current == null) {
            return;
        }
        Instant now = clock.instant();
        if (now.isAfter(current.lastPongAt.plus(PONG_TIMEOUT))) {
            log.warn("토스 웹소켓 pong 없음 - 재연결한다");
            closeConnection();
            scheduleReconnect(1);
            return;
        }
        if (!targets.equals(current.declared)) {
            current.session.declareTrades(targets);
            current.declared = targets;
        }
        if (now.isAfter(current.lastPingAt.plus(PING_INTERVAL))) {
            current.session.ping();
            current.lastPingAt = now;
        }
        if (now.isAfter(current.openedAt.plus(STABLE_SESSION))) {
            consecutiveFailures.set(0);
        }
    }

    private Map<TossRealtimeMarket, List<String>> withoutRejected(Map<TossRealtimeMarket, List<String>> targets) {
        Map<TossRealtimeMarket, List<String>> filtered = new EnumMap<>(TossRealtimeMarket.class);
        targets.forEach((market, codes) -> filtered.put(market, codes.stream()
            .filter(code -> !rejectedTargets.contains(market.tradeChannel() + ":" + code))
            .toList()));
        return filtered;
    }

    @Nullable
    private Connection openConnection() {
        Connection current = connection;
        if (current != null && current.session.isOpen()) {
            return current;
        }
        connection = null;
        if (clock.instant().isBefore(nextConnectAt)) {
            return null;
        }
        String revoked = revokedToken;
        if (revoked != null) {
            invalidateIfCurrent(revoked);
            revokedToken = null;
        }
        String token = tossTokenManager.getAccessToken();
        try {
            PendingListener listener = new PendingListener();
            TossRealtimeSession session = tossRealtimeClient.connect(token, listener);
            Connection opened = new Connection(token, session, clock.instant());
            listener.bind(opened);
            connection = opened;
            rejectedTargets.clear();
            count(METRIC_CONNECTS, "success");
            return opened;
        } catch (TossRealtimeHandshakeException e) {
            count(METRIC_CONNECTS, "failure");
            handleConnectFailure(e, token);
            return null;
        }
    }

    private void handleConnectFailure(TossRealtimeHandshakeException e, String token) {
        consecutiveFailures.incrementAndGet();
        if (e.getStatusCode() == STATUS_UNAUTHORIZED) {
            invalidateIfCurrent(token);
            scheduleReconnect(AUTH_FAILURE_BACKOFF_SECONDS);
        } else if (e.getStatusCode() == STATUS_FORBIDDEN) {
            scheduleReconnect(FORBIDDEN_BACKOFF_SECONDS);
        } else {
            scheduleBackoff();
        }
        log.warn("토스 웹소켓 연결 실패: status={}, failures={}", e.getStatusCode(), consecutiveFailures.get());
    }

    // 다른 프로세스가 이미 새 토큰을 받아 뒀다면 지우지 않는다 - 지우면 또 재발급돼 그쪽을 끊는다.
    private void invalidateIfCurrent(String token) {
        if (token.equals(tossTokenManager.getAccessToken())) {
            tossTokenManager.invalidateToken();
        }
    }

    private void scheduleBackoff() {
        long exponential = 1L << Math.min(consecutiveFailures.get(), 6);
        long seconds = Math.min(exponential, MAX_BACKOFF_SECONDS);
        scheduleReconnect(seconds + ThreadLocalRandom.current().nextLong(seconds + 1));
    }

    private void scheduleReconnect(long seconds) {
        nextConnectAt = clock.instant().plusSeconds(seconds);
    }

    private void closeConnection() {
        Connection current = connection;
        connection = null;
        pendingBySymbol.clear();
        if (current != null) {
            current.session.close();
        }
    }

    private void flushOnce() {
        if (pendingBySymbol.isEmpty()) {
            return;
        }
        if (!priceRelayLeaderGate.isLeader()) {
            pendingBySymbol.clear();
            return;
        }
        List<TossRealtimeTrade> trades = new ArrayList<>();
        for (String symbol : List.copyOf(pendingBySymbol.keySet())) {
            TossRealtimeTrade trade = pendingBySymbol.remove(symbol);
            if (trade != null && isMarketOpen(trade.market()) && priceTopicSubscriptionTracker.hasSubscribers(symbol)) {
                trades.add(trade);
            }
        }
        publish(trades, TossRealtimeMarket.KR, domesticPreviousCloseCache);
        publish(trades, TossRealtimeMarket.US, overseasPreviousCloseCache);
    }

    private boolean isMarketOpen(TossRealtimeMarket market) {
        return market == TossRealtimeMarket.KR
            ? domesticMarketCalendarCache.isMarketOpenNow()
            : overseasMarketCalendarCache.isMarketOpenNow();
    }

    private void publish(List<TossRealtimeTrade> trades, TossRealtimeMarket market, PreviousCloseCache previousCloses) {
        List<TossRealtimeTrade> marketTrades = trades.stream().filter(trade -> trade.market() == market).toList();
        if (marketTrades.isEmpty()) {
            return;
        }
        Map<String, Double> previousCloseBySymbol = previousCloses.get(
            marketTrades.stream().map(TossRealtimeTrade::symbol).toList());
        for (TossRealtimeTrade trade : marketTrades) {
            Double changeRate = ChangeRateCalculator.calculate(trade.price(), previousCloseBySymbol.get(trade.symbol()));
            messagingTemplate.convertAndSend(PRICE_TOPIC_PREFIX + trade.symbol(), new PriceSnapshot(
                trade.symbol(), trade.price(), changeRate, trade.timestamp().toString()));
        }
        meterRegistry.counter(METRIC_PUBLISHED, "market", market.getCode()).increment(marketTrades.size());
    }

    private void count(String metric, String result) {
        meterRegistry.counter(metric, "result", result).increment();
    }

    /** 연결 객체가 만들어지기 전에 온 프레임은 버린다. 구독을 선언하기 전이라 시세일 수 없다. */
    private static final class PendingListener implements TossRealtimeListener {

        @Nullable
        private volatile Connection target;

        private void bind(Connection connection) {
            this.target = connection;
        }

        @Override
        public void onTrade(TossRealtimeTrade trade) {
            Connection current = target;
            if (current != null) {
                current.onTrade(trade);
            }
        }

        @Override
        public void onSubscriptions(int subscribedCount, List<String> rejected) {
            Connection current = target;
            if (current != null) {
                current.onSubscriptions(rejected, subscribedCount);
            }
        }

        @Override
        public void onError(String code, String message) {
            Connection current = target;
            if (current != null) {
                current.onError(code, message);
            }
        }

        @Override
        public void onPong() {
            Connection current = target;
            if (current != null) {
                current.onPong();
            }
        }

        @Override
        public void onClosed(String reason) {
            Connection current = target;
            if (current != null) {
                current.onClosed(reason);
            }
        }
    }

    private final class Connection {

        private final String token;
        private final TossRealtimeSession session;
        private final Instant openedAt;
        @Nullable
        private volatile Map<TossRealtimeMarket, List<String>> declared;
        private volatile Instant lastPongAt;
        private Instant lastPingAt;
        private volatile boolean revoked;

        private Connection(String token, TossRealtimeSession session, Instant openedAt) {
            this.token = token;
            this.session = session;
            this.openedAt = openedAt;
            this.lastPongAt = openedAt;
            this.lastPingAt = openedAt;
        }

        private void onTrade(TossRealtimeTrade trade) {
            if (connection != this) {
                return;
            }
            meterRegistry.counter(METRIC_FRAMES, "market", trade.market().getCode()).increment();
            Instant oldest = clock.instant().minus(MAX_TRADE_AGE);
            if (trade.volume() > 0 && trade.timestamp().toInstant().isAfter(oldest)) {
                pendingBySymbol.put(trade.symbol(), trade);
            }
        }

        private void onSubscriptions(List<String> rejected, int subscribedCount) {
            rejectedTargets.addAll(rejected);
            log.info("토스 웹소켓 구독 확정: subscribed={}, rejected={}", subscribedCount, rejected);
        }

        private void onError(String code, String message) {
            log.warn("토스 웹소켓 에러 프레임: code={}, message={}", code, message);
            if (ERROR_AUTH_REVOKED.equals(code)) {
                revoked = true;
            } else if (ERROR_RATE_LIMITED.equals(code)) {
                declared = null;
            }
        }

        private void onPong() {
            lastPongAt = clock.instant();
        }

        private void onClosed(String reason) {
            if (connection != this) {
                return;
            }
            log.info("토스 웹소켓 연결 종료: {}", reason);
            connection = null;
            consecutiveFailures.incrementAndGet();
            if (revoked) {
                revokedToken = token;
                scheduleReconnect(AUTH_FAILURE_BACKOFF_SECONDS);
            } else {
                scheduleBackoff();
            }
        }
    }
}
