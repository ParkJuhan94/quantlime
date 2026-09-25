package com.quantlime.notification.repository;

import com.quantlime.notification.domain.Notification;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Slice<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUser_IdAndIsReadFalse(Long userId);

    @Modifying
    @Query("update Notification n set n.isRead = true where n.user.id = :userId and n.isRead = false")
    void markAllAsRead(@Param("userId") Long userId);

    // 보존기간(14일) 정리 - 파생 delete 쿼리라 호출측(NotificationCleanupScheduler)에
    // @Transactional이 반드시 있어야 한다(글로벌 컨벤션 "리포지토리" 섹션 참고).
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
