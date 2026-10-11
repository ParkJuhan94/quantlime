package com.quantlime.event.observability;

import com.quantlime.event.market.MarketTopics;
import com.quantlime.event.payment.PaymentTopics;
import com.quantlime.event.score.ScoreTopics;
import com.quantlime.event.subscription.SubscriptionTopics;
import com.quantlime.event.telegramfeed.TelegramFeedTopics;
import com.quantlime.event.videofeed.VideoFeedTopics;
import java.util.Map;

/**
 * {@code @RetryableTopic}을 쓰는 메인 토픽과 DLT 알림 도메인 라벨의 대응표(2026-10-01).
 * 각 컨슈머의 {@code @DltHandler}가 {@link KafkaDltNotifier#notify}에 넘기는 도메인
 * 문자열과 같은 값이다 - 같은 {@code dlt.messages} 카운터 시리즈를 포이즌 필
 * 옵저버({@link DeserializationFailureDltObserver})와 공유해야 알림 규칙의
 * {@code $labels.domain}이 한 가지로 모인다.
 *
 * <p>새 {@code @RetryableTopic} 컨슈머를 추가하면 여기도 한 줄 추가해야 한다 - 빠뜨리면
 * 카운터 선등록({@link DltMetricsBinder})과 옵저버 도메인 라벨에서 "unknown"으로
 * 떨어진다. {@code DltTopicCatalogTest}가 컨슈머 목록과 이 표를 대조해 누락을 막는다.
 */
public final class DltTopicCatalog {

    public static final String UNKNOWN = "unknown";

    private static final Map<String, String> DOMAIN_BY_TOPIC = Map.of(
        VideoFeedTopics.VIDEO_SELECTED, "videofeed-transcript",
        VideoFeedTopics.VIDEO_TRANSCRIBED, "videofeed-summary",
        MarketTopics.PRICE_REFRESH_REQUESTED, "market-price-refresh",
        SubscriptionTopics.SUBSCRIPTION_RENEWAL_DUE, "subscription-renewal",
        PaymentTopics.PAYMENT_WEBHOOK_RECEIVED, "payment-webhook",
        TelegramFeedTopics.TELEGRAM_DIGEST_GENERATION_REQUESTED, "telegram-digest",
        ScoreTopics.QUADRANT_ALERT_REQUESTED, "score-quadrant-alert");

    private DltTopicCatalog() {
    }

    public static Map<String, String> domainByTopic() {
        return DOMAIN_BY_TOPIC;
    }

    /** 카탈로그에 없는 토픽은 {@link #UNKNOWN} - 메트릭 태그 카디널리티를 닫아두기 위함. */
    public static String domainOf(String topic) {
        return DOMAIN_BY_TOPIC.getOrDefault(topic, UNKNOWN);
    }
}
