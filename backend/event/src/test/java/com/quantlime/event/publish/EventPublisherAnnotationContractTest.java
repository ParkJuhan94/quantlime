package com.quantlime.event.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.ClassUtils;

/**
 * 퍼블리셔의 {@code @TransactionalEventListener}는 {@code AFTER_COMMIT}(롤백된 트랜잭션의 이벤트가
 * 브로커로 새지 않게)과 {@code fallbackExecution = true}(트랜잭션 밖에서 발행해도 조용히 스킵되지
 * 않게)를 둘 다 가져야 한다. 하나라도 빠지면 이벤트가 유실되거나 롤백된 변경이 발행되는데, 컴파일도
 * 단위 테스트도 이를 잡지 못해 계약 테스트로 고정한다.
 */
@Tag("unit")
class EventPublisherAnnotationContractTest {

    @Test
    @DisplayName("[모든 퍼블리셔 리스너가 AFTER_COMMIT + fallbackExecution=true다]")
    void everyTransactionalEventListenerIsAfterCommitWithFallback() throws ClassNotFoundException {
        // given
        List<String> violations = new ArrayList<>();
        int found = 0;
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        // when
        for (BeanDefinition definition : scanner.findCandidateComponents("com.quantlime.event")) {
            Class<?> type = ClassUtils.forName(definition.getBeanClassName(), getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                TransactionalEventListener listener =
                    AnnotatedElementUtils.findMergedAnnotation(method, TransactionalEventListener.class);
                if (listener == null) {
                    continue;
                }
                found++;
                if (listener.phase() != TransactionPhase.AFTER_COMMIT || !listener.fallbackExecution()) {
                    violations.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }

        // then - 스캔이 아무것도 못 찾아 조용히 통과하는 일을 막는 하한(현재 6개: videofeed 2 + 나머지 4)
        assertThat(found).isGreaterThanOrEqualTo(6);
        assertThat(violations).isEmpty();
    }
}
