package com.quantlime.telegramfeed.scheduler;

import com.quantlime.telegramfeed.service.TelegramPostRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * VideoRetentionScheduler(새벽 3시)보다 10분 늦춰(03:10) 실행한다 - 다른 텔레그램
 * 스케줄러들과 동일하게 유튜브 파이프라인과 로그를 시간순으로 구분하기 위함
 * (동시 실행 자체는 락 키가 서로 달라 문제없지만 관례를 맞춘다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramPostRetentionScheduler {

    private final TelegramPostRetentionService telegramPostRetentionService;

    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            telegramPostRetentionService.runExclusively().ifPresentOrElse(
                deletedCount -> log.info("텔레그램 글 보존 기간 정리 완료: 삭제건수={}", deletedCount),
                () -> log.info("이미 다른 실행이 텔레그램 보존 기간 정리 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("텔레그램 글 보존 기간 정리 실패: reason={}", e.getMessage(), e);
        }
    }
}
