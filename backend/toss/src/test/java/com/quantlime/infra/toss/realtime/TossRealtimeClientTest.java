package com.quantlime.infra.toss.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsSci;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 실제 웹소켓 왕복으로 확인한다. JDK 리스너의 프레임 조립·흐름 제어는 mock으로 검증되지 않는다. */
@Tag("unit")
class TossRealtimeClientTest {

    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<String> AUTH_HEADERS = new CopyOnWriteArrayList<>();
    private static volatile Session serverSession;

    private Tomcat tomcat;
    private int port;

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<TossRealtimeTrade> trades = new CopyOnWriteArrayList<>();
    private final TossRealtimeListener listener = new TossRealtimeListener() {
        @Override
        public void onTrade(TossRealtimeTrade trade) {
            trades.add(trade);
        }

        @Override
        public void onSubscriptions(int subscribedCount, List<String> rejectedTargets) {
            events.add("ack:" + subscribedCount + ":" + rejectedTargets);
        }

        @Override
        public void onError(String code, String message) {
            events.add("error:" + code);
        }

        @Override
        public void onPong() {
            events.add("pong");
        }

        @Override
        public void onClosed(String reason) {
            events.add("closed");
        }
    };

    public static class FakeTossEndpoint extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            serverSession = session;
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String text) {
                    RECEIVED.add(text);
                }
            });
        }
    }

    public static class HeaderCapturingConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
            AUTH_HEADERS.addAll(request.getHeaders().getOrDefault("Authorization", List.of()));
        }
    }

    @BeforeEach
    void startServer() throws Exception {
        RECEIVED.clear();
        AUTH_HEADERS.clear();
        serverSession = null;
        tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("toss-ws").toString());
        tomcat.setPort(0);
        Context context = tomcat.addContext("", null);
        context.addServletContainerInitializer(new WsSci(), null);
        context.addServletContainerInitializer((classes, servletContext) -> {
            ServerContainer container = (ServerContainer) servletContext.getAttribute(ServerContainer.class.getName());
            try {
                container.addEndpoint(ServerEndpointConfig.Builder.create(FakeTossEndpoint.class, "/ws/v1")
                    .configurator(new HeaderCapturingConfigurator())
                    .build());
            } catch (jakarta.websocket.DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }, null);
        // WsFilter는 필터 체인이 있어야 도는데, 매핑된 서블릿이 없으면 체인 전에 404가 된다.
        Tomcat.addServlet(context, "default", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        });
        context.addServletMappingDecoded("/", "default");
        tomcat.getConnector();
        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    private TossRealtimeClient client(String path) {
        return new TossRealtimeClient("ws://localhost:" + port + path, new ObjectMapper());
    }

    // 클라이언트는 101 응답으로 연결을 끝내지만 서버의 onOpen은 그 뒤에 돌 수 있다. 기다리지 않으면
    // 아직 없는 세션이나 이전 테스트의 세션을 쓰게 된다(CI에서 실제 발생).
    private TossRealtimeSession connect() {
        TossRealtimeSession session = client("/ws/v1").connect("token-1", listener);
        await().atMost(Duration.ofSeconds(5)).until(() -> serverSession != null);
        return session;
    }

    private static void serverSends(String text) throws IOException {
        serverSession.getBasicRemote().sendText(text);
    }

    @Test
    @DisplayName("[Bearer 토큰으로 접속하고 체결·ack·pong 프레임을 리스너로 전달한다]")
    void connectsAndDispatchesFrames() throws IOException {
        // given
        TossRealtimeSession session = connect();

        // when
        serverSends("{\"type\":\"subscriptions\",\"id\":\"r\",\"subscribed\":[\"trade:us:AAPL\"],"
            + "\"rejected\":[{\"target\":\"trade:kr:KOSPI\",\"code\":\"stock-not-found\",\"message\":\"x\"}]}");
        serverSends("{\"type\":\"message\",\"topic\":\"trade:us:AAPL\",\"data\":{\"price\":\"351.82\","
            + "\"volume\":\"10\",\"timestamp\":\"2026-11-05T20:04:17.502+09:00\",\"currency\":\"USD\"}}");
        serverSends("{\"type\":\"pong\"}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> events.contains("pong") && !trades.isEmpty());
        assertThat(AUTH_HEADERS).containsExactly("Bearer token-1");
        assertThat(events).contains("ack:1:[trade:kr:KOSPI]");
        TossRealtimeTrade trade = trades.get(0);
        assertThat(trade.market()).isEqualTo(TossRealtimeMarket.US);
        assertThat(trade.symbol()).isEqualTo("AAPL");
        assertThat(trade.price()).isEqualTo(351.82);
        session.close();
    }

    @Test
    @DisplayName("[구독 선언은 빈 시장을 뺀 JSON 배열 하나로, PING은 대문자 텍스트로 보낸다]")
    void sendsDeclarationAndPing() {
        // given
        TossRealtimeSession session = connect();

        // when
        session.declareTrades(Map.of(TossRealtimeMarket.KR, List.of("005930"), TossRealtimeMarket.US, List.of()));
        session.ping();

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> RECEIVED.size() == 2);
        assertThat(RECEIVED.get(0)).isEqualTo("[{\"type\":\"trade:kr\",\"codes\":[\"005930\"]}]");
        assertThat(RECEIVED.get(1)).isEqualTo("PING");
        session.close();
    }

    @Test
    @DisplayName("[여러 조각으로 온 프레임을 합쳐서 처리하고 깨진 프레임은 연결을 유지한 채 버린다]")
    void reassemblesPartialFrames() throws IOException {
        // given
        TossRealtimeSession session = connect();

        // when
        serverSession.getBasicRemote().sendText("{\"type\":", false);
        serverSession.getBasicRemote().sendText("\"pong\"}", true);
        serverSends("not-json");
        serverSends("{\"type\":\"error\",\"error\":{\"code\":\"auth-revoked\",\"message\":\"x\"}}");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> events.contains("error:auth-revoked"));
        assertThat(events).contains("pong");
        assertThat(session.isOpen()).isTrue();
        session.close();
    }

    @Test
    @DisplayName("[서버가 연결을 닫으면 리스너에 알리고 세션은 닫힌 상태가 된다]")
    void notifiesOnServerClose() throws IOException {
        // given
        TossRealtimeSession session = connect();

        // when
        serverSession.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "deploy"));

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> events.contains("closed"));
        assertThat(session.isOpen()).isFalse();
    }

    @Test
    @DisplayName("[핸드셰이크가 거부되면 응답 코드를 담아 던진다]")
    void reportsHandshakeStatus() {
        assertThatThrownBy(() -> client("/missing").connect("token-1", listener))
            .isInstanceOf(TossRealtimeHandshakeException.class)
            .satisfies(e -> assertThat(((TossRealtimeHandshakeException) e).getStatusCode()).isEqualTo(401));
    }
}
