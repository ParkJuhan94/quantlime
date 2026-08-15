package com.quantlime.telegramfeed.scheduler;

import com.quantlime.telegramfeed.dto.TelegramCollectResult;
import com.quantlime.telegramfeed.service.TelegramCollectionFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 정각 전체 텔레그램 채널 수집을 실행한다(2026-08-15부로 하루 3회
 * → 1시간마다로 상향) - 텔레그램은 유튜브(채널당 하루 1~3편)와 달리
 * 채널에 따라 하루 수십~70건 이상 올라오기도 해(실측: "미국 주식
 * 인사이더" 하루 70건), 8~9시간 간격이면 한 번의 수집에 페이지네이션
 * 요청이 몰려 스크래핑 부담이 커지고 다이제스트 재료도 뒤늦게 반영된다.
 * 이 스케줄러는 Gemini 호출이 없는 순수 스크래핑이라 쿼터 부담이 없어
 * 자유롭게 올릴 수 있다 - 반면 다이제스트 재생성(Gemini 호출)은 이
 * 빈도를 그대로 따라가면 안 돼(하루 24회는 유튜브와 공유하는 무료 티어
 * 일일 쿼터를 초과) TelegramDigestScheduler로 분리해 기존 하루 3회
 * 빈도를 유지한다. 락은 TelegramCollectionFacade가 소유한다(관리자
 * 수동 트리거도 같은 락을 거쳐야 스케줄러와의 동시 실행을 막을 수 있음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramCollectionScheduler {

    private final TelegramCollectionFacade telegramCollectionFacade;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
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
