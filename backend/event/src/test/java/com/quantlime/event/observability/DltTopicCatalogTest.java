package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** 새 {@code @RetryableTopic} 컨슈머를 추가하고 카탈로그를 빠뜨리면 DLT 알림 라벨이 unknown으로 떨어진다. */
@Tag("unit")
class DltTopicCatalogTest {

    @Test
    @DisplayName("[@RetryableTopic을 쓰는 모든 컨슈머의 토픽이 카탈로그에 등록돼 있다]")
    void everyRetryableTopicConsumerTopicIsInCatalog() throws Exception {
        // given
        List<String> topics = retryableListenerTopics();

        // then - 스캔이 아무것도 못 찾아 조용히 통과하는 일을 막는 하한(현재 6개)
        assertThat(topics).hasSizeGreaterThanOrEqualTo(6);
        assertThat(DltTopicCatalog.domainByTopic().keySet()).containsAll(topics);
    }

    @Test
    @DisplayName("[카탈로그의 도메인 라벨은 서로 중복되지 않는다 - 알림에서 도메인으로 토픽을 구분하기 위함]")
    void domainLabelsAreUnique() {
        assertThat(DltTopicCatalog.domainByTopic().values()).doesNotHaveDuplicates();
    }

    private List<String> retryableListenerTopics() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<String> topics = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.quantlime.event")) {
            Class<?> type = ClassUtils.forName(definition.getBeanClassName(), getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                KafkaListener listener = AnnotatedElementUtils.findMergedAnnotation(method, KafkaListener.class);
                RetryableTopic retryable = AnnotatedElementUtils.findMergedAnnotation(method, RetryableTopic.class);
                if (listener != null && retryable != null) {
                    topics.addAll(Arrays.asList(listener.topics()));
                }
            }
        }
        return topics;
    }
}
