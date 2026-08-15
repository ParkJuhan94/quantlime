package com.quantlime.telegramfeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// VideoRetentionService와 구조적으로 동일 - 유튜브와 같은 보존 기간(14일)을 쓴다.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPostRetentionService {

    private static final int RETENTION_DAYS = 14;
    private static final String LOCK_KEY = "lock:telegram-retention-cleanup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final RedisLockService redisLockService;
    private final TelegramPostRepository telegramPostRepository;
    private final TelegramPostRetentionDeleteService telegramPostRetentionDeleteService;

    /**
     * 락을 잡은 채로 deletePostsOlderThanRetention()을 실행한다.
     * TelegramPostRetentionScheduler·TelegramFeedAdminController(수동 트리거)
     * 둘 다 이 진입점만 호출해야 동시에 같은 행을 지우려는 중복 실행을 막을 수 있다.
     */
    public Optional<Integer> runExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::deletePostsOlderThanRetention);
    }

    // self-invocation(위 runExclusively) 경로가 있어 여기 @Transactional을
    // 붙이면 프록시를 안 타 무시된다 - 실제 삭제 실행은 별도 빈에 위임한다
    // (VideoRetentionService와 동일 패턴).
    public int deletePostsOlderThanRetention() {
        LocalDateTime cutoff = LocalDate.now().minusDays(RETENTION_DAYS).atStartOfDay();
        List<Long> postIds = telegramPostRepository.findIdsByPublishedAtBefore(cutoff);
        if (postIds.isEmpty()) {
            log.info("보존 기간({}일) 초과 텔레그램 글 없음 - 삭제할 데이터 없음", RETENTION_DAYS);
            return 0;
        }

        // telegram_post_id를 NO_CONSTRAINT FK로만 참조해 DB 캐스케이드가 없다 -
        // 자식 테이블부터 먼저 지워야 고아 행이 안 남는다.
        telegramPostRetentionDeleteService.deleteBatch(postIds);

        log.info("보존 기간({}일) 초과 텔레그램 글 삭제 완료: 삭제건수={}, cutoff={}",
            RETENTION_DAYS, postIds.size(), cutoff);
        return postIds.size();
    }
}
