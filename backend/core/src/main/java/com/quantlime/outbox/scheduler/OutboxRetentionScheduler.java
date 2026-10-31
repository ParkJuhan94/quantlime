package com.quantlime.outbox.scheduler;

import com.quantlime.outbox.service.OutboxService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발행 완료된 아웃박스 행을 7일 보관한 뒤 지운다(그 사이엔 "그날 무엇을 보냈나"를 테이블에서 확인할 수 있다).
 * 운영 EC2가 00:00~07:40 중지돼 새벽 시각은 못 돌아서 기동 뒤인 08:10에 돈다. 미발행(PENDING) 행은 지우지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRetentionScheduler {

    static final int RETENTION_DAYS = 7;

    private final OutboxService outboxService;
    private final Clock clock;

    @Scheduled(cron = "0 10 8 * * *", zone = "Asia/Seoul")
    public void purgePublished() {
        try {
            outboxService.purgePublishedBefore(LocalDateTime.now(clock).minusDays(RETENTION_DAYS));
        } catch (Exception e) {
            log.error("아웃박스 정리 실패: reason={}", e.getMessage(), e);
        }
    }
}
