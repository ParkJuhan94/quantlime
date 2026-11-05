package com.quantlime.event.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerLagMonitorTest {

    private static final String GROUP = "price-refresh-collector";
    private static final String TOPIC = "price-refresh-requested";
    private static final TopicPartition P0 = new TopicPartition(TOPIC, 0);
    private static final TopicPartition P1 = new TopicPartition(TOPIC, 1);

    @Mock
    private KafkaListenerEndpointRegistry registry;

    @Mock
    private MessageListenerContainer container;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T06:36:00Z"));
    private final FakeOffsetReader reader = new FakeOffsetReader();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private KafkaConsumerLagMonitor monitor;

    @BeforeEach
    void setUp() {
        given(registry.getListenerContainers()).willReturn(List.of(container));
        given(container.getGroupId()).willReturn(GROUP);
        given(container.getAssignedPartitions()).willReturn(List.of(P0, P1));
        monitor = new KafkaConsumerLagMonitor(registry, reader, meterRegistry, clock);
    }

    @Test
    @DisplayName("lag는 로그 끝에서 커밋 오프셋을 뺀 값을 토픽별로 합산한다")
    void lag_isEndMinusCommitted() {
        // given
        reader.ends.put(P0, 1000L);
        reader.ends.put(P1, 500L);
        reader.committed.put(P0, 900L);
        reader.committed.put(P1, 100L);

        // when
        monitor.refresh();

        // then
        assertThat(lag()).isEqualTo(500.0);
        assertThat(committedTotal()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("커밋이 한 번도 없으면 가장 오래된 오프셋부터 센다 - 첫 배치가 커밋되기 전 멈춤도 보인다")
    void lag_withoutCommit_countsFromBeginning() {
        // given
        reader.beginnings.put(P0, 0L);
        reader.beginnings.put(P1, 0L);
        reader.ends.put(P0, 40L);
        reader.ends.put(P1, 60L);

        // when
        monitor.refresh();

        // then
        assertThat(lag()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("lag가 있는데 커밋 오프셋이 안 움직이면 stalled 시간이 쌓이고, 움직이면 0부터 다시 센다")
    void stalled_accumulatesWhileNoProgress_andResetsOnProgress() {
        // given
        reader.ends.put(P0, 1000L);
        reader.ends.put(P1, 0L);
        reader.committed.put(P0, 100L);
        reader.committed.put(P1, 0L);
        monitor.refresh();
        assertThat(stalled()).isZero();

        // when - 20분 동안 커밋이 그대로
        clock.advance(Duration.ofMinutes(20));
        monitor.refresh();

        // then
        assertThat(stalled()).isEqualTo(1200.0);

        // when - 커밋이 한 건이라도 움직임
        clock.advance(Duration.ofSeconds(30));
        reader.committed.put(P0, 101L);
        monitor.refresh();

        // then
        assertThat(stalled()).isZero();
    }

    @Test
    @DisplayName("lag가 0으로 해소되면 stalled도 0이다 - 정상 배치는 오래 걸려도 멈춤이 아니다")
    void stalled_isZeroWhenCaughtUp() {
        // given
        reader.ends.put(P0, 1000L);
        reader.ends.put(P1, 0L);
        reader.committed.put(P0, 1000L);
        reader.committed.put(P1, 0L);

        // when
        clock.advance(Duration.ofHours(1));
        monitor.refresh();

        // then
        assertThat(lag()).isZero();
        assertThat(stalled()).isZero();
    }

    @Test
    @DisplayName("브로커 조회가 실패하면 지표를 유지하고 collect.age만 커진다")
    void collectFailure_keepsValues_andAgeGrows() {
        // given
        reader.ends.put(P0, 10L);
        reader.ends.put(P1, 0L);
        reader.committed.put(P0, 0L);
        reader.committed.put(P1, 0L);
        monitor.refresh();
        reader.fail = true;

        // when
        clock.advance(Duration.ofMinutes(7));
        monitor.refresh();

        // then
        assertThat(lag()).isEqualTo(10.0);
        assertThat(meterRegistry.get(KafkaConsumerLagMonitor.COLLECT_AGE).gauge().value()).isEqualTo(420.0);
    }

    private double lag() {
        return meterRegistry.get(KafkaConsumerLagMonitor.LAG).tag("group", GROUP).tag("topic", TOPIC).gauge()
            .value();
    }

    private double committedTotal() {
        return meterRegistry.get(KafkaConsumerLagMonitor.COMMITTED).tag("group", GROUP).functionCounter().count();
    }

    private double stalled() {
        return meterRegistry.get(KafkaConsumerLagMonitor.STALLED).tag("group", GROUP).gauge().value();
    }

    private static final class FakeOffsetReader implements KafkaOffsetReader {

        private final Map<TopicPartition, Long> committed = new HashMap<>();
        private final Map<TopicPartition, Long> ends = new HashMap<>();
        private final Map<TopicPartition, Long> beginnings = new HashMap<>();
        private boolean fail;

        @Override
        public Map<TopicPartition, Long> committedOffsets(String groupId) {
            failIfNeeded();
            return new HashMap<>(committed);
        }

        @Override
        public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions) {
            failIfNeeded();
            return new HashMap<>(ends);
        }

        @Override
        public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions) {
            failIfNeeded();
            return new HashMap<>(beginnings);
        }

        private void failIfNeeded() {
            if (fail) {
                throw new IllegalStateException("브로커 조회 실패");
            }
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
