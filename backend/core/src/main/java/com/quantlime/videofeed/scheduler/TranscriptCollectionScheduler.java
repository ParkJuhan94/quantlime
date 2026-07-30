package com.quantlime.videofeed.scheduler;

import com.quantlime.videofeed.service.TranscriptCollectionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FeedCollectionScheduler(하루 3회, 07/12/19시)가 새로 선정한 SELECTED
 * 영상이 반영될 시간을 두려고 30분 늦춰(07:30/12:30/19:30) 실행한다.
 * 두 스케줄러를 하나로 합치지 않은 이유는 책임 분리 - 수집은 빠르고(REST
 * 호출 위주) 자막 조회는 느리고 실패율이 더 높은 외부 스크래핑이라, 같은
 * 락/같은 실행 흐름으로 묶으면 한쪽의 실패 프로파일이 다른 쪽에도 영향을
 * 준다. 락 자체는 TranscriptCollectionFacade가 소유한다(2026-07-30 -
 * 관리자 수동 트리거(/api/admin/feed/transcribe)도 같은 락을 거쳐야
 * 스케줄러와의 동시 실행을 막을 수 있어, 스케줄러 안에서만 잡던 락을 두
 * 호출자가 공유하는 파사드로 옮겼다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptCollectionScheduler {

    private final TranscriptCollectionFacade transcriptCollectionFacade;

    @Scheduled(cron = "0 30 7,12,19 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            transcriptCollectionFacade.runBatchExclusively().ifPresentOrElse(
                results -> log.info("자막 수집 완료: results={}", results),
                () -> log.info("이미 다른 실행이 자막 수집 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("자막 수집 스케줄 실행 실패: reason={}", e.getMessage(), e);
        }
    }
}
