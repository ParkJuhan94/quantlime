package com.quantlime.telegramfeed.scheduler;

import com.quantlime.telegramfeed.service.TelegramSummaryCollectionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SummaryCollectionScheduler(유튜브, 08:00/13:00/20:00)보다 30분 늦춰
 * (08:30/13:30/20:30) 실행한다 - 같은 Gemini 무료 티어 일일 쿼터를 공유하므로
 * RPM 경합을 원천 차단하고, 429 발생 시 로그로 어느 파이프라인이 원인인지
 * 구분할 수 있다(docs/ROADMAP.md "Gemini 무료 티어 일 20회 쿼터 공유" 참고).
 * 락은 TelegramSummaryCollectionFacade가 소유한다(관리자 수동 트리거도 같은
 * 락을 거쳐야 스케줄러와의 동시 실행을 막을 수 있음 - SummaryCollectionScheduler와
 * 동일 패턴).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSummaryCollectionScheduler {

    private final TelegramSummaryCollectionFacade telegramSummaryCollectionFacade;

    @Scheduled(cron = "0 30 8,13,20 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            telegramSummaryCollectionFacade.runBatchExclusively().ifPresentOrElse(
                results -> log.info("텔레그램 AI 요약 생성 완료: results={}", results),
                () -> log.info("이미 다른 실행이 텔레그램 AI 요약 생성 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("텔레그램 AI 요약 생성 스케줄 실행 실패: reason={}", e.getMessage(), e);
        }
    }
}
