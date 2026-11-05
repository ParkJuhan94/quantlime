package com.quantlime.event.observability;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 컨슈머 그룹 lag 관측(2026-11-05) - 브로커의 커밋 오프셋을 기준으로 그룹·토픽별 lag, 처리 속도, "처리가 멈춘 시간"을
 * Micrometer 지표로 내보낸다.
 *
 * <p>카프카 클라이언트가 기본 제공하는 {@code kafka.consumer.fetch.manager.records.lag*}를 쓰지 않은 이유: 그 값은
 * "가져온 위치 대 로그 끝"이라 이미 받아 둔 메시지를 처리하다 멈춘 컨슈머(핸들러 블로킹)도 0으로 보인다. 실험에서
 * 핸들러를 50초 막은 동안 {@code records.lag.max}가 계속 0이었다. 커밋 오프셋은 처리가 끝난 만큼만 움직이므로 멈춤을
 * 그대로 드러낸다. 한 번도 커밋하지 않은 그룹은 가장 오래된 오프셋부터 센다 - 첫 배치가 커밋되기 전에 멈추면 커밋이
 * 없어 lag가 비어 보이는 사각지대를 막기 위해서다.
 *
 * <p>알림 기준으로 "lag가 N분 넘게 0보다 큼"을 쓰지 않는다. 가격 팬아웃은 정상일 때도 수십 분 동안 backlog가 남기 때문에
 * 매 배치마다 오탐이 된다. 대신 "lag가 있는데 커밋 오프셋이 N분 동안 안 움직임"({@code stalled.seconds})을 본다.
 * 이 값은 앱이 재시작하면 0부터 다시 센다.
 *
 * <p>대상 그룹은 이 프로세스에 등록된 리스너 컨테이너의 그룹뿐이다. 브로커에 남은 옛 그룹의 오프셋이 알림으로 새지
 * 않게 하기 위해서다. 브로커 조회가 실패하면 지표를 갱신하지 않고 {@code collect.age}가 커진다 - 낡은 값이 "정상"처럼
 * 보이는 것을 그 지표로 감지한다.
 */
@Slf4j
@Component
public class KafkaConsumerLagMonitor {

    static final String LAG = "kafka.consumer.group.lag";
    static final String COMMITTED = "kafka.consumer.group.committed.offsets";
    static final String STALLED = "kafka.consumer.group.stalled";
    static final String COLLECT_AGE = "kafka.consumer.group.lag.collect.age";

    private final KafkaListenerEndpointRegistry registry;
    private final KafkaOffsetReader offsetReader;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Map<String, AtomicLong> lagByGroupTopic = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> committedByGroupTopic = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> stalledSecondsByGroup = new ConcurrentHashMap<>();
    private final Map<String, Progress> progressByGroup = new HashMap<>();
    private final AtomicLong lastSuccessEpochSecond;

    public KafkaConsumerLagMonitor(KafkaListenerEndpointRegistry registry, KafkaOffsetReader offsetReader,
        MeterRegistry meterRegistry, Clock clock) {
        this.registry = registry;
        this.offsetReader = offsetReader;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.lastSuccessEpochSecond = new AtomicLong(Instant.now(clock).getEpochSecond());
        Gauge.builder(COLLECT_AGE, this, monitor -> monitor.collectAgeSeconds())
            .baseUnit("seconds")
            .description("마지막으로 컨슈머 그룹 오프셋 조회에 성공한 뒤 지난 시간")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void refresh() {
        try {
            collect();
        } catch (RuntimeException e) {
            log.warn("컨슈머 그룹 lag 수집 실패(이전 값을 유지): {}", e.getMessage());
        }
    }

    private void collect() {
        Map<String, Set<TopicPartition>> assignedByGroup = assignedPartitionsByGroup();
        Instant now = Instant.now(clock);
        for (Map.Entry<String, Set<TopicPartition>> entry : assignedByGroup.entrySet()) {
            String group = entry.getKey();
            Map<TopicPartition, Long> committed = offsetReader.committedOffsets(group);
            Set<TopicPartition> partitions = new HashSet<>(committed.keySet());
            partitions.addAll(entry.getValue());
            Map<TopicPartition, Long> ends = offsetReader.endOffsets(partitions);
            Map<TopicPartition, Long> beginnings = offsetReader.beginningOffsets(partitions);
            update(group, partitions, committed, ends, beginnings, now);
        }
        lastSuccessEpochSecond.set(now.getEpochSecond());
    }

    private void update(String group, Collection<TopicPartition> partitions, Map<TopicPartition, Long> committed,
        Map<TopicPartition, Long> ends, Map<TopicPartition, Long> beginnings, Instant now) {
        Map<String, long[]> lagAndPositionByTopic = new HashMap<>();
        for (TopicPartition partition : partitions) {
            Long end = ends.get(partition);
            Long position = committed.getOrDefault(partition, beginnings.get(partition));
            if (end == null || position == null) {
                continue;
            }
            long[] sums = lagAndPositionByTopic.computeIfAbsent(partition.topic(), topic -> new long[2]);
            sums[0] += Math.max(0, end - position);
            sums[1] += position;
        }
        long totalLag = 0;
        long totalPosition = 0;
        for (Map.Entry<String, long[]> entry : lagAndPositionByTopic.entrySet()) {
            String key = group + '|' + entry.getKey();
            holder(lagByGroupTopic, key, group, entry.getKey(), true).set(entry.getValue()[0]);
            holder(committedByGroupTopic, key, group, entry.getKey(), false).set(entry.getValue()[1]);
            totalLag += entry.getValue()[0];
            totalPosition += entry.getValue()[1];
        }
        long currentPosition = totalPosition;
        Progress progress = progressByGroup.computeIfAbsent(group, g -> new Progress(currentPosition, now));
        if (totalLag == 0 || totalPosition > progress.position) {
            progress.lastProgressAt = now;
        }
        progress.position = totalPosition;
        long stalledSeconds = totalLag == 0 ? 0 : Math.max(0, now.getEpochSecond() - progress.lastProgressAt.getEpochSecond());
        stalledSecondsByGroup.computeIfAbsent(group, g -> {
            AtomicLong holder = new AtomicLong();
            Gauge.builder(STALLED, holder, AtomicLong::get)
                .baseUnit("seconds")
                .tag("group", g)
                .description("lag가 있는데 커밋 오프셋이 움직이지 않은 시간(lag가 0이면 0)")
                .register(meterRegistry);
            return holder;
        }).set(stalledSeconds);
    }

    private AtomicLong holder(Map<String, AtomicLong> holders, String key, String group, String topic, boolean lag) {
        return holders.computeIfAbsent(key, k -> {
            AtomicLong holder = new AtomicLong();
            if (lag) {
                Gauge.builder(LAG, holder, AtomicLong::get)
                    .tag("group", group).tag("topic", topic)
                    .description("커밋 오프셋 기준 컨슈머 그룹 lag(커밋이 없으면 가장 오래된 오프셋부터)")
                    .register(meterRegistry);
            } else {
                FunctionCounter.builder(COMMITTED, holder, AtomicLong::get)
                    .tag("group", group).tag("topic", topic)
                    .description("그룹이 처리를 마친 누적 오프셋 합 - rate()로 처리 속도를 본다")
                    .register(meterRegistry);
            }
            return holder;
        });
    }

    private Map<String, Set<TopicPartition>> assignedPartitionsByGroup() {
        Map<String, Set<TopicPartition>> assigned = new HashMap<>();
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            String group = container.getGroupId();
            if (group == null) {
                continue;
            }
            Set<TopicPartition> partitions = assigned.computeIfAbsent(group, g -> new HashSet<>());
            Collection<TopicPartition> containerPartitions = container.getAssignedPartitions();
            if (containerPartitions != null) {
                partitions.addAll(containerPartitions);
            }
        }
        return assigned;
    }

    private double collectAgeSeconds() {
        return Math.max(0, Instant.now(clock).getEpochSecond() - lastSuccessEpochSecond.get());
    }

    private static final class Progress {
        private long position;
        private Instant lastProgressAt;

        private Progress(long position, Instant lastProgressAt) {
            this.position = position;
            this.lastProgressAt = lastProgressAt;
        }
    }
}
