package com.quantlime.videofeed.scheduler;

import com.quantlime.videofeed.service.FeedCollectionFacade;
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
                results -> log.info("피드 수집 완료: results={}", results),
                () -> log.info("이미 다른 실행이 피드 수집 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("피드 수집 스케줄 실행 실패: reason={}", e.getMessage(), e);
        }
    }
}
