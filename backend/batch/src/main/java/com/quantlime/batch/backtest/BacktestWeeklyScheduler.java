package com.quantlime.batch.backtest;

import com.quantlime.batch.common.BatchLaunchRejectedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 백테스트(데이터셋 준비 → 종목별 백테스트 → 횡단면 Rank IC)를 주 1회 자동으로 갱신한다(2026-09-13 신설).
 * Rank IC가 5~60일 horizon 기준이라 일 단위로 재계산해도 의미 있는 변화가 크지 않은 반면 종목당 축×horizon마다
 * block bootstrap 500회를 도는 무거운 연산이라, 매일이 아니라 주 1회로 고정한다. 금요일 16:30은 15:36 국내 배치 직후이고,
 * 서버가 00:00~07:40에 중지되므로(#194) 그 전에 마무리되도록 앞당긴 시각이다. 그래도 중지 시각에 걸리면 재기동 때 STARTED로
 * 남은 실행이 FAILED로 정리되어 관리자 API의 restart로 끊긴 지점부터 이어 할 수 있다.
 *
 * <p>2026-10-22부로 순차 실행하던 본문을 Spring Batch Job({@code backtestWeeklyJob})으로 옮겼고, 이 스케줄러는 Job을 시작만
 * 한다. 이전에는 Redis 락({@code lock:backtest-weekly})으로 수동 트리거와의 중복을 막았는데, 이제는 같은 그룹의 백테스트 Job이
 * 실행 중이면 시작이 거부되는 것으로 대신한다({@code BatchJobGroups}). 중간에 끊겨도 관리자 API로 restart해 끊긴 지점부터
 * 이어 할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BacktestWeeklyScheduler {

    private final BacktestBatchLauncher launcher;

    @Scheduled(cron = "0 30 16 * * FRI", zone = "Asia/Seoul")
    public void runWeeklyBacktest() {
        try {
            long executionId = launcher.launchWeekly();
            log.info("주간 백테스트 자동 갱신 시작: executionId={}", executionId);
        } catch (BatchLaunchRejectedException e) {
            log.info("주간 백테스트 자동 갱신 스킵: {}", e.getMessage());
        } catch (Exception e) {
            log.error("주간 백테스트 자동 갱신 시작 실패: reason={}", e.getMessage(), e);
        }
    }
}
