package com.quantlime.telegramfeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.telegramfeed.dto.TelegramRetentionResult;
import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// VideoRetentionService와 구조적으로 동일 - 유튜브와 같은 보존 기간(14일)을
// 쓴다. 다이제스트 재설계(2026-08-15) 이후 TelegramPost(발행일 기준)와
// TelegramDigest(digest_date 기준) 둘 다 정리 대상이다.
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPostRetentionService {

    private static final int RETENTION_DAYS = 14;
    private static final String LOCK_KEY = "lock:telegram-retention-cleanup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final RedisLockService redisLockService;
    private final TelegramPostRepository telegramPostRepository;
    private final TelegramDigestRepository telegramDigestRepository;
    private final TelegramPostRetentionDeleteService telegramPostRetentionDeleteService;

    /**
     * 락을 잡은 채로 deleteOlderThanRetention()을 실행한다.
     * TelegramPostRetentionScheduler·TelegramFeedAdminController(수동 트리거)
     * 둘 다 이 진입점만 호출해야 동시에 같은 행을 지우려는 중복 실행을 막을 수 있다.
     */
    public Optional<TelegramRetentionResult> runExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::deleteOlderThanRetention);
    }

    // self-invocation(위 runExclusively) 경로가 있어 여기 @Transactional을
    // 붙이면 프록시를 안 타 무시된다 - 실제 삭제 실행은 별도 빈에 위임한다
    // (VideoRetentionService와 동일 패턴).
    public TelegramRetentionResult deleteOlderThanRetention() {
        LocalDateTime postCutoff = LocalDate.now().minusDays(RETENTION_DAYS).atStartOfDay();
        List<Long> postIds = telegramPostRepository.findIdsByPublishedAtBefore(postCutoff);
        if (!postIds.isEmpty()) {
            telegramPostRetentionDeleteService.deletePostBatch(postIds);
        }

        LocalDate digestCutoff = LocalDate.now().minusDays(RETENTION_DAYS);
        List<Long> digestIds = telegramDigestRepository.findIdsByDigestDateBefore(digestCutoff);
        if (!digestIds.isEmpty()) {
            telegramPostRetentionDeleteService.deleteDigestBatch(digestIds);
        }

        log.info("보존 기간({}일) 초과 텔레그램 데이터 삭제 완료: 글삭제건수={}, 다이제스트삭제건수={}, "
                + "postCutoff={}, digestCutoff={}",
            RETENTION_DAYS, postIds.size(), digestIds.size(), postCutoff, digestCutoff);
        return new TelegramRetentionResult(postIds.size(), digestIds.size());
    }
}
