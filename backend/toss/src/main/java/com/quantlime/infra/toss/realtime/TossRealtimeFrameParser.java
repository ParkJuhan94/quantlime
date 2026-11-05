package com.quantlime.infra.toss.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 수신 프레임을 top-level {@code type}으로 나눠 리스너에 전달한다. 스펙은 필드 추가를 예고하므로
 * 모르는 프레임은 버리고, 깨진 프레임 하나가 연결을 끊지 않게 예외를 밖으로 던지지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
class TossRealtimeFrameParser {

    private static final int TOPIC_PARTS = 3;

    private final ObjectMapper objectMapper;

    void dispatch(String text, TossRealtimeListener listener) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            log.warn("토스 웹소켓 프레임 파싱 실패: {}", e.getOriginalMessage());
            return;
        }
        switch (root.path("type").asText("")) {
            case "message" -> dispatchMessage(root, listener);
            case "subscriptions" -> dispatchSubscriptions(root, listener);
            case "error" -> {
                JsonNode error = root.path("error");
                listener.onError(error.path("code").asText(""), error.path("message").asText(""));
            }
            case "pong" -> listener.onPong();
            default -> log.debug("알 수 없는 토스 웹소켓 프레임 무시: type={}", root.path("type").asText(""));
        }
    }

    private void dispatchMessage(JsonNode root, TossRealtimeListener listener) {
        String[] topic = root.path("topic").asText("").split(":", -1);
        if (topic.length != TOPIC_PARTS || !"trade".equals(topic[0])) {
            return;
        }
        TossRealtimeMarket market = TossRealtimeMarket.fromCode(topic[1]);
        if (market == null) {
            return;
        }
        JsonNode data = root.path("data");
        try {
            listener.onTrade(new TossRealtimeTrade(
                market,
                topic[2],
                Double.parseDouble(data.path("price").asText()),
                Double.parseDouble(data.path("volume").asText()),
                OffsetDateTime.parse(data.path("timestamp").asText())));
        } catch (NumberFormatException | DateTimeParseException e) {
            log.warn("토스 웹소켓 체결 프레임 값 오류: topic={}, reason={}", root.path("topic").asText(), e.getMessage());
        }
    }

    private void dispatchSubscriptions(JsonNode root, TossRealtimeListener listener) {
        List<String> rejected = new ArrayList<>();
        for (JsonNode item : root.path("rejected")) {
            rejected.add(item.path("target").asText());
        }
        listener.onSubscriptions(root.path("subscribed").size(), rejected);
    }
}
