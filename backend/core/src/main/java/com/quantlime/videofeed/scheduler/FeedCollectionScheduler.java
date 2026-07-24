package com.quantlime.videofeed.scheduler;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.videofeed.dto.CollectResult;
import com.quantlime.videofeed.service.FeedCollectionFacade;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 3회(07/12/19시) 전체 채널 수집 + PENDING_REVIEW 재평가를 실행한다
 * (§2 P5). 스케일아웃 시 여러 인스턴스가 동시에 도는 것을 막기 위해
 * Redis 락으로 한 번에 하나만 실행되도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedCollectionScheduler {

    private static final String LOCK_KEY = "lock:feed-collect";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RedisLockService redisLockService;
    private final FeedCollectionFacade feedCollectionFacade;

    @Scheduled(cron = "0 0 7,12,19 * * *", zone = "Asia/Seoul")
    public void run() {
        if (!redisLockService.tryLock(LOCK_KEY, LOCK_TTL)) {
            log.info("이미 다른 인스턴스가 피드 수집 중 - 이번 실행은 스킵");
            return;
        }
        try {
            List<CollectResult> results = feedCollectionFacade.runAll();
            log.info("피드 수집 완료: results={}", results);
            feedCollectionFacade.reevaluatePendingReview();
        } catch (Exception e) {
            log.error("피드 수집 스케줄 실행 실패: reason={}", e.getMessage(), e);
        } finally {
            redisLockService.unlock(LOCK_KEY);
        }
    }
}
