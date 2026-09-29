package com.quantlime.telegramfeed.scheduler;

import com.quantlime.telegramfeed.service.TelegramDigestGenerationFacade;
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
 *
 * <p>2026-09-30부로 실제 생성은 이 스레드가 아니라 Kafka 컨슈머
 * (TelegramDigestGenerationConsumer, event 모듈)가 채널별로 처리한다(카프카
 * 다도메인 확장 Phase 4) - 이 스케줄러는 채널마다 이벤트를 발행만 하고
 * 끝나므로, 채널별 성공/실패 요약을 여기서 더 이상 볼 수 없다(각 컨슈머의
 * 로그/DLT 알림으로 확인).
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
                count -> log.info("텔레그램 다이제스트 생성 이벤트 발행 완료: {}건", count),
                () -> log.info("이미 다른 실행이 텔레그램 다이제스트 생성 중 - 이번 실행은 스킵"));
        } catch (Exception e) {
            log.error("텔레그램 다이제스트 생성 스케줄 실행 실패: reason={}", e.getMessage(), e);
        }
    }
}
