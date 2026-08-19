package com.quantlime.telegramfeed.scheduler;

import com.quantlime.telegramfeed.dto.TelegramDigestGenerateResult;
import com.quantlime.telegramfeed.service.TelegramDigestGenerationFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SummaryCollectionScheduler(유튜브, 08:00/13:00/20:00)보다 30분 늦춰
 * (08:30/13:30/20:30) 실행한다 - 같은 Gemini 무료 티어 일일 쿼터를 공유하므로
 * RPM 경합을 원천 차단한다(2026-08-15 다이제스트 재설계 이후에도 이 스케줄은
 * 유지 - TelegramCollectionScheduler(수집)가 1시간마다로 상향됐다고 해서 이
 * 스케줄까지 따라가면 쿼터를 초과한다. "수집 사이클마다 누적 재요약"의 의미는
 * 수집이 촘촘히 돌아 이 스케줄이 실행되는 시점엔 이미 최신 글까지 반영돼
 * 있다는 뜻이지, 다이제스트 재생성 자체가 1시간마다라는 뜻이 아니다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramDigestGenerationScheduler {

    private final TelegramDigestGenerationFacade telegramDigestGenerationFacade;

    @Scheduled(cron = "0 30 8,13,20 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            telegramDigestGenerationFacade.runAllExclusively().ifPresentOrElse(
                this::logSummary,
                () -> log.info("이미 다른 실행이 텔레그램 다이제스트 생성 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("텔레그램 다이제스트 생성 스케줄 실행 실패: reason={}", e.getMessage(), e);
        }
    }

    private void logSummary(List<TelegramDigestGenerateResult> results) {
        List<TelegramDigestGenerateResult> failures = results.stream().filter(r -> !r.success()).toList();
        if (!failures.isEmpty()) {
            // 실패해도 예외를 던지지 않고 이전 다이제스트가 그대로 서빙되는 구조라
            // (PythonEngineClient 클래스 주석 참고) 조용히 묻히기 쉽다 - info보다
            // 눈에 띄게 warn으로 격상.
            log.warn("텔레그램 다이제스트 생성 일부 실패: failures={}", failures);
        }
        log.info("텔레그램 다이제스트 생성 완료: results={}", results);
    }
}
