package com.quantlime.backtest.scheduler;

import com.quantlime.backtest.domain.BacktestDailyScore;
import com.quantlime.backtest.repository.BacktestDailyScoreRepository;
import com.quantlime.backtest.service.BacktestDatasetPreparationService;
import com.quantlime.backtest.service.BacktestUniverseService;
import com.quantlime.backtest.service.CrossSectionalBacktestService;
import com.quantlime.common.lock.RedisLockService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 백테스트(유니버스 선정+백필 → 종목별 백테스트 → 횡단면 Rank IC)를 주 1회
 * 자동으로 갱신한다(2026-09-13 신설 - 이관 이후 자동 트리거 자체가 없어
 * backtest_result가 계속 비어있던 걸 발견해, 그동안 써온 수동 어드민 API
 * 3단계를 그대로 묶었다). Rank IC가 5~60일 horizon 기준이라 일 단위로
 * 재계산해도 의미 있는 변화가 크지 않은 반면(사용자와 협의해 결정),
 * run-universe가 종목당 축×horizon마다 block bootstrap 500회를 도는
 * 무거운 연산이라 daily로 얹으면 리소스 부담이 누적된다 - 그래서
 * 가격/스코어(트리거1)처럼 매일이 아니라 주 1회(금요일 정규장+NXT 마감,
 * 20:10 스코어 확정 배치 이후)로 고정한다.
 *
 * <p>세 단계 모두 같은 Redis 락(runExclusively) 아래서 순차 실행해, 이
 * 스케줄과 관리자 수동 트리거(BacktestAdminController)가 겹쳐 돌지 않게
 * 한다 - MarketDataRefreshService의 락 패턴과 동일.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BacktestWeeklyScheduler {

    private static final String LOCK_KEY = "lock:backtest-weekly";
    private static final Duration LOCK_TTL = Duration.ofHours(3);

    private final RedisLockService redisLockService;
    private final BacktestDatasetPreparationService backtestDatasetPreparationService;
    private final BacktestUniverseService backtestUniverseService;
    private final CrossSectionalBacktestService crossSectionalBacktestService;
    private final BacktestDailyScoreRepository backtestDailyScoreRepository;

    @Scheduled(cron = "0 0 21 * * FRI", zone = "Asia/Seoul")
    public void runWeeklyBacktest() {
        try {
            redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, () -> {
                runSequence();
                return true;
            }).ifPresentOrElse(
                result -> log.info("주간 백테스트 자동 갱신 완료"),
                () -> log.info("이미 다른 백테스트 실행이 진행 중 - 이번 주간 자동 갱신은 스킵"));
        } catch (Exception e) {
            log.error("주간 백테스트 자동 갱신 실패: reason={}", e.getMessage(), e);
        }
    }

    private void runSequence() {
        log.info("주간 백테스트 자동 갱신 시작");
        backtestDatasetPreparationService.prepareDataset();
        backtestUniverseService.runUniverse(false);

        String scoreVersion = backtestDailyScoreRepository.findTopByOrderByIdDesc()
            .map(BacktestDailyScore::getScoreVersion)
            .orElse(null);
        if (scoreVersion == null) {
            log.warn("주간 백테스트 자동 갱신: backtest_daily_score가 비어있어 횡단면 백테스트를 건너뜀");
            return;
        }
        crossSectionalBacktestService.runAllMarkets(scoreVersion, false, 200);
    }
}
