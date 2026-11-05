package com.quantlime.event.observability;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * {@link Admin} 클라이언트로 브로커의 컨슈머 그룹 오프셋을 읽는다. 클라이언트는 한 번 만들어 재사용하고, 실패하면 닫아서
 * 다음 호출에서 새로 만든다. 요청 타임아웃을 짧게 둬서(5초) 브로커가 멈춰도 스케줄러 스레드를 오래 붙들지 않는다.
 */
@Slf4j
@Component
class AdminKafkaOffsetReader implements KafkaOffsetReader {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final KafkaAdmin kafkaAdmin;
    @Nullable
    private Admin admin;

    AdminKafkaOffsetReader(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public synchronized Map<TopicPartition, Long> committedOffsets(String groupId) {
        return call(client -> {
            Map<TopicPartition, OffsetAndMetadata> committed =
                client.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                    .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Map<TopicPartition, Long> result = new HashMap<>();
            committed.forEach((partition, offset) -> {
                if (offset != null) {
                    result.put(partition, offset.offset());
                }
            });
            return result;
        });
    }

    @Override
    public synchronized Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions) {
        return listOffsets(partitions, OffsetSpec.latest());
    }

    @Override
    public synchronized Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions) {
        return listOffsets(partitions, OffsetSpec.earliest());
    }

    private Map<TopicPartition, Long> listOffsets(Collection<TopicPartition> partitions, OffsetSpec spec) {
        if (partitions.isEmpty()) {
            return Map.of();
        }
        return call(client -> {
            Map<TopicPartition, OffsetSpec> request = new HashMap<>();
            partitions.forEach(partition -> request.put(partition, spec));
            Map<TopicPartition, ListOffsetsResultInfo> response =
                client.listOffsets(request).all().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Map<TopicPartition, Long> result = new HashMap<>();
            response.forEach((partition, info) -> result.put(partition, info.offset()));
            return result;
        });
    }

    private <T> T call(AdminCall<T> call) {
        try {
            return call.apply(client());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("카프카 오프셋 조회 중 인터럽트", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // 연결이 깨진 클라이언트를 계속 쓰지 않도록 닫고 다음 호출에서 새로 만든다.
            closeClient();
            throw new IllegalStateException("카프카 오프셋 조회 실패: " + e.getMessage(), e);
        }
    }

    private Admin client() {
        if (admin == null) {
            Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
            config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
            config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
            admin = Admin.create(config);
        }
        return admin;
    }

    private void closeClient() {
        if (admin != null) {
            try {
                admin.close(Duration.ofSeconds(1));
            } catch (RuntimeException e) {
                log.debug("카프카 Admin 클라이언트 종료 중 예외 무시: {}", e.getMessage());
            }
            admin = null;
        }
    }

    @PreDestroy
    synchronized void shutdown() {
        closeClient();
    }

    @FunctionalInterface
    private interface AdminCall<T> {
        T apply(Admin client) throws InterruptedException, ExecutionException, TimeoutException;
    }
}
