package com.quantlime.videofeed.scheduler;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.videofeed.dto.SummarizeResult;
import com.quantlime.videofeed.service.SummaryCollectionFacade;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TranscriptCollectionScheduler(07:30/12:30/19:30)가 새로 자막을 채운
 * TRANSCRIBED 영상이 반영될 시간을 두려고 30분 더 늦춰(08:00/13:00/20:00)
 * 실행한다. 세 스케줄러(수집→자막→요약)를 하나로 합치지 않은 이유는
 * TranscriptCollectionScheduler와 동일 - 단계별로 책임과 실패 프로파일이
 * 달라(요약은 외부 LLM 호출이라 지연/실패 양상이 또 다름) 같은 락/실행
 * 흐름으로 묶으면 한 단계의 실패가 다른 단계에 영향을 준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryCollectionScheduler {

    private static final String LOCK_KEY = "lock:summary-collect";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RedisLockService redisLockService;
    private final SummaryCollectionFacade summaryCollectionFacade;

    @Scheduled(cron = "0 0 8,13,20 * * *", zone = "Asia/Seoul")
    public void run() {
        if (!redisLockService.tryLock(LOCK_KEY, LOCK_TTL)) {
            log.info("이미 다른 인스턴스가 AI 요약 생성 중 - 이번 실행은 스킵");
            return;
        }
        try {
            List<SummarizeResult> results = summaryCollectionFacade.runBatch();
            log.info("AI 요약 생성 완료: results={}", results);
        } catch (Exception e) {
            log.error("AI 요약 생성 스케줄 실행 실패: reason={}", e.getMessage(), e);
        } finally {
            redisLockService.unlock(LOCK_KEY);
        }
    }
}
