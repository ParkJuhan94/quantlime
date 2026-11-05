package com.quantlime.infra.toss.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * 토스 실시간 웹소켓(AsyncAPI 3.0, docs/toss-asyncapi.json) 클라이언트. 프로토콜만 다루고
 * 어떤 종목을 언제 구독할지, 재연결을 언제 할지는 호출 측이 정한다 - 계정당 연결이 2개뿐이라
 * 여기서 임의로 재연결하면 다른 인스턴스의 연결을 밀어낼 수 있기 때문이다.
 *
 * <p>JDK {@link HttpClient}를 쓴다. 이 프로세스의 STOMP 서버 쪽 의존성과 섞이지 않고 새
 * 라이브러리도 필요 없다.
 */
@Slf4j
public class TossRealtimeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final long CLOSE_TIMEOUT_SECONDS = 3;
    private static final String PING_FRAME = "PING";
    private static final int NORMAL_CLOSURE = 1000;

    private final URI uri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TossRealtimeFrameParser frameParser;

    public TossRealtimeClient(String url, ObjectMapper objectMapper) {
        this.uri = URI.create(url);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.frameParser = new TossRealtimeFrameParser(objectMapper);
    }

    /** 연결이 맺어지거나 실패할 때까지 호출 스레드를 막는다(연결 제한 + {@value #SEND_TIMEOUT_SECONDS}초). */
    public TossRealtimeSession connect(String accessToken, TossRealtimeListener listener) {
        try {
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .buildAsync(uri, new FrameListener(listener))
                .get(CONNECT_TIMEOUT.toSeconds() + SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new JdkSession(webSocket);
        } catch (ExecutionException e) {
            int status = e.getCause() instanceof WebSocketHandshakeException handshake
                ? handshake.getResponse().statusCode()
                : 0;
            throw new TossRealtimeHandshakeException(status, e.getCause());
        } catch (TimeoutException e) {
            throw new TossRealtimeHandshakeException(0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TossRealtimeHandshakeException(0, e);
        }
    }

    private final class FrameListener implements WebSocket.Listener {

        private final TossRealtimeListener listener;
        private final StringBuilder partial = new StringBuilder();

        private FrameListener(TossRealtimeListener listener) {
            this.listener = listener;
        }

        @Override
        @Nullable
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                try {
                    frameParser.dispatch(text, listener);
                } catch (RuntimeException e) {
                    // 리스너 예외로 수신 루프가 멈추면 연결이 살아 있어도 프레임이 끊긴다.
                    log.warn("토스 웹소켓 프레임 처리 실패: {}", e.toString());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        @Nullable
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            listener.onClosed("close " + statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            // 스펙상 서버는 close 코드 없이 끊을 수 있어 비정상 종료도 정상 경로다.
            listener.onClosed("error " + error);
        }
    }

    private final class JdkSession implements TossRealtimeSession {

        private final WebSocket webSocket;

        private JdkSession(WebSocket webSocket) {
            this.webSocket = webSocket;
        }

        @Override
        public void declareTrades(Map<TossRealtimeMarket, List<String>> codesByMarket) {
            List<Map<String, Object>> declaration = new ArrayList<>();
            codesByMarket.forEach((market, codes) -> {
                if (!codes.isEmpty()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", market.tradeChannel());
                    item.put("codes", codes);
                    declaration.add(item);
                }
            });
            try {
                send(objectMapper.writeValueAsString(declaration));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("구독 선언 직렬화 실패", e);
            }
        }

        @Override
        public void ping() {
            send(PING_FRAME);
        }

        @Override
        public boolean isOpen() {
            return !webSocket.isInputClosed() && !webSocket.isOutputClosed();
        }

        @Override
        public void close() {
            try {
                if (!webSocket.isOutputClosed()) {
                    webSocket.sendClose(NORMAL_CLOSURE, "").orTimeout(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
                }
            } catch (CompletionException e) {
                log.debug("토스 웹소켓 종료 프레임 전송 실패: {}", e.toString());
            } finally {
                webSocket.abort();
            }
        }

        private void send(String text) {
            webSocket.sendText(text, true).orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        }
    }
}
