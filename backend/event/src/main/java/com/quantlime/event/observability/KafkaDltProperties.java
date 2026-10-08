package com.quantlime.event.observability;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * DLT 관측 설정. {@code slack-suppressed-domains}에 든 도메인은 Slack 알림만 보내지 않는다 -
 * Prometheus 카운터({@code dlt.messages})는 그대로 증가해 Alertmanager 규칙/라우팅이 판단한다.
 *
 * <p>운영 자막 수집(videofeed-transcript)은 YouTube가 클라우드 IP를 차단(RequestBlocked)해 영상마다 DLT가
 * 쌓이는데, 자막은 로컬에서 가져와 운영에 동기화하는 방식으로 운영하므로 개별 Slack 알림을 끈다
 * ({@code application-prod.yml}). 기본값은 빈 집합이라 로컬/테스트는 모든 도메인을 그대로 알린다.
 */
@ConfigurationProperties(prefix = "kafka.dlt")
public record KafkaDltProperties(@DefaultValue Set<String> slackSuppressedDomains) {
}
