package com.quantlime.videofeed.scheduler;

import com.quantlime.videofeed.dto.CollectResult;
import com.quantlime.videofeed.service.FeedCollectionFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 3회(07/12/19시) 전체 채널 수집 + PENDING_REVIEW 재평가를 실행한다
 * (§2 P5). 스케일아웃 시 여러 인스턴스가 동시에 도는 것을 막기 위해
 * Redis 락으로 한 번에 하나만 실행되도록 한다 - 락 자체는 FeedCollectionFacade가
 * 소유한다(2026-07-30 - 관리자 수동 트리거(/api/admin/feed/collect)도 같은
 * 락을 거쳐야 스케줄러와의 동시 실행을 막을 수 있어, 스케줄러 안에서만
 * 잡던 락을 두 호출자가 공유하는 파사드로 옮겼다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedCollectionScheduler {

    private final FeedCollectionFacade feedCollectionFacade;

    @Scheduled(cron = "0 0 7,12,19 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            feedCollectionFacade.runAllExclusively().ifPresentOrElse(
                this::logSummary,
                () -> log.info("이미 다른 실행이 피드 수집 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("피드 수집 스케줄 실행 실패: reason={}", e.getMessage(), e);
        }
    }

    // 채널이 늘어날수록 결과 레코드를 한 줄에 통째로 dump하면 스캔하기
    // 어려워져, 총계 한 줄 + 실패 채널만 별도로 남긴다.
    private void logSummary(List<CollectResult> results) {
        int discoveredTotal = results.stream().mapToInt(CollectResult::discoveredCount).sum();
        long failedCount = results.stream().filter(result -> !result.success()).count();
        log.info("피드 수집 완료: 채널={}, 신규발견={}건, 실패={}건",
            results.size(), discoveredTotal, failedCount);
        results.stream()
            .filter(result -> !result.success())
            .forEach(result -> log.warn("채널 수집 실패: channelName={}, error={}",
                result.channelName(), result.errorMessage()));
    }
}
