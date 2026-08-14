package com.quantlime.telegramfeed.scheduler;

import com.quantlime.telegramfeed.dto.TelegramCollectResult;
import com.quantlime.telegramfeed.service.TelegramCollectionFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 3회(07:10/12:10/19:10) 전체 텔레그램 채널 수집을 실행한다.
 * FeedCollectionScheduler(유튜브, 07/12/19시)보다 10분 늦춰 로그를 시간순
 * 으로 읽기 쉽게 한다 - 두 파이프라인이 서로 다른 외부 서비스(t.me vs
 * YouTube API)를 부르므로 경합은 없다. 락은 TelegramCollectionFacade가
 * 소유한다(관리자 수동 트리거도 같은 락을 거쳐야 스케줄러와의 동시 실행을
 * 막을 수 있음 - FeedCollectionScheduler와 동일 패턴).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramCollectionScheduler {

    private final TelegramCollectionFacade telegramCollectionFacade;

    @Scheduled(cron = "0 10 7,12,19 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            telegramCollectionFacade.runAllExclusively().ifPresentOrElse(
                this::logSummary,
                () -> log.info("이미 다른 실행이 텔레그램 수집 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("텔레그램 수집 스케줄 실행 실패: reason={}", e.getMessage(), e);
        }
    }

    private void logSummary(List<TelegramCollectResult> results) {
        int discoveredTotal = results.stream().mapToInt(TelegramCollectResult::discoveredCount).sum();
        long failedCount = results.stream().filter(result -> !result.success()).count();
        log.info("텔레그램 수집 완료: 채널={}, 신규발견={}건, 실패={}건",
            results.size(), discoveredTotal, failedCount);
        results.stream()
            .filter(result -> !result.success())
            .forEach(result -> log.warn("텔레그램 채널 수집 실패: channelName={}, error={}",
                result.channelName(), result.errorMessage()));
    }
}
