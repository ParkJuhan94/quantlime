package com.quantlime.event.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * {@code dlt.messages} 카운터를 기동 시점에 0으로 미리 등록한다(2026-10-01). 카운터가
 * 첫 DLT 때 값 1로 늦게 생기면 Prometheus가 본 첫 샘플이 이미 1이라
 * {@code increase(dlt_messages_total[15m]) > 0}이 "증가"를 감지하지 못한다 - 도메인/토픽별
 * 첫 번째 DLT는 백스톱 알림(Alertmanager)이 울리지 않는 사각지대였다. 0에서 시작하는
 * 시리즈가 먼저 있으면 0→1 전이가 그대로 증가로 잡힌다.
 */
@Component
public class DltMetricsBinder implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        DltTopicCatalog.domainByTopic().forEach((topic, domain) ->
            KafkaDltNotifier.counter(registry, domain, topic));
    }
}
