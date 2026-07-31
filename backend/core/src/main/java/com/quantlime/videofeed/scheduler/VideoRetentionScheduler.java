package com.quantlime.videofeed.scheduler;

import com.quantlime.videofeed.service.VideoRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 3시 - 수집(07/12/19시)/자막(07:30/12:30/19:30)/요약(08/13/20시)
 * 스케줄과 겹치지 않는 한가한 시간대에 보존 기간 초과 영상을 정리한다.
 * 관리자 수동 트리거(/api/admin/feed/retention/cleanup)와 락을 공유해
 * 동시 실행을 막는다(FeedCollectionScheduler와 동일 패턴).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoRetentionScheduler {

    private final VideoRetentionService videoRetentionService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            videoRetentionService.runExclusively().ifPresentOrElse(
                deletedCount -> log.info("영상 데이터 보존 기간 정리 완료: 삭제건수={}", deletedCount),
                () -> log.info("이미 다른 실행이 보존 기간 정리 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("영상 데이터 보존 기간 정리 실패: reason={}", e.getMessage(), e);
        }
    }
}
