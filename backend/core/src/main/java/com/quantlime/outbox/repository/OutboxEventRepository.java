package com.quantlime.outbox.repository;

import com.quantlime.outbox.domain.OutboxEvent;
import com.quantlime.outbox.domain.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    boolean existsByDedupKey(String dedupKey);

    // 릴레이 폴링 대상 - 커밋 직후 즉시 발행이 진행 중일 수 있는 갓 생긴 행(createdBefore 이후)은 건너뛴다.
    @Query("select e from OutboxEvent e where e.status = :status and e.createdAt < :createdBefore order by e.id asc")
    List<OutboxEvent> findRelayTargets(
        @Param("status") OutboxStatus status,
        @Param("createdBefore") LocalDateTime createdBefore,
        Pageable pageable);

    long countByStatus(OutboxStatus status);

    Optional<OutboxEvent> findFirstByStatusOrderByIdAsc(OutboxStatus status);

    // 벌크 삭제라 호출부(OutboxService.purgePublishedBefore)에 @Transactional이 있어야 한다.
    @Modifying
    @Query("delete from OutboxEvent e where e.status = :status and e.publishedAt < :cutoff")
    int deleteByStatusAndPublishedAtBefore(
        @Param("status") OutboxStatus status, @Param("cutoff") LocalDateTime cutoff);
}
