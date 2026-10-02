package com.quantlime.event.publish;

import com.quantlime.event.observability.DltTopicCatalog;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * {@code kafka.publish.failures} 카운터를 기동 시 토픽별 0으로 미리 등록한다 - 첫 실패 때 늦게
 * 생기면 Prometheus {@code increase()}가 첫 증가를 못 보는 사각지대가 있다({@code
 * DltMetricsBinder}와 같은 이유).
 */
@Component
public class KafkaPublishMetricsBinder implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        DltTopicCatalog.domainByTopic().keySet().forEach(topic ->
            KafkaEventSender.failureCounter(registry, topic));
    }
}
