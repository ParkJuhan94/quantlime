package com.quantlime.event.observability;

import java.util.Collection;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;

/**
 * 브로커가 아는 컨슈머 그룹 오프셋을 읽는 포트. 실제 구현은 {@link AdminKafkaOffsetReader}이고, 계산 로직
 * ({@link KafkaConsumerLagMonitor})은 이 인터페이스만 보게 해서 브로커 없이 테스트한다.
 */
interface KafkaOffsetReader {

    /** 그룹이 커밋한 오프셋. 한 번도 커밋하지 않은 파티션은 키에 없다. */
    Map<TopicPartition, Long> committedOffsets(String groupId);

    /** 파티션별 로그 끝 오프셋(다음에 쓰일 오프셋). */
    Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions);

    /** 파티션별 가장 오래된 오프셋. 커밋이 없는 그룹의 lag 기준점이다. */
    Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions);
}
