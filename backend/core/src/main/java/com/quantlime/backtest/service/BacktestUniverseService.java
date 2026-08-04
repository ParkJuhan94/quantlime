package com.quantlime.backtest.service;

import com.quantlime.backtest.repository.BacktestResultRepository;
import com.quantlime.market.service.DomesticUniverseSelectionService;
import com.quantlime.market.service.OverseasUniverseSelectionService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 백테스트용 유니버스(국내+해외 거래대금 상위 500씩) 전체에 대해
 * {@link BacktestService#runBacktest}를 순회 실행한다 - 트리거1(가격/스코어
 * 갱신)과 달리 자동(기동 시/16:00) 트리거에는 절대 연결하지 않는다. 백테스트는
 * 축(추세추종/평균회귀) × horizon(5/10/20/60일)마다 block bootstrap을
 * 500회 반복하는 무거운 연산이라, 가격/스코어 갱신처럼 매 기동마다 자동으로
 * 돌리면 개발 중 재기동할 때마다 유니버스 전체를 매번 재계산하게 돼 부담이
 * 크다(2026-07-25 논의) - 그래서 오직 수동 트리거(/dev/backtest/run-universe)
 * 로만 노출한다.
 *
 * <p>유니버스 선정 메서드를 다시 호출해 이 서비스만 단독으로 불러도 데이터가
 * 준비되게 하되(이미 최신이면 count-based 스킵이라 비용이 거의 없음), 오늘
 * 이미 백테스트가 돈 종목은 재계산을 건너뛴다(가격/스코어와 달리 아직 이
 * 스킵 로직이 없어 매번 100% 재계산되던 문제를 여기서부터 막는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestUniverseService {

    private final DomesticUniverseSelectionService domesticUniverseSelectionService;
    private final OverseasUniverseSelectionService overseasUniverseSelectionService;
    private final BacktestService backtestService;
    private final BacktestResultRepository backtestResultRepository;

    public void runUniverse() {
        runUniverse(false);
    }

    /**
     * @param force true면 오늘 이미 백테스트가 돈 종목도 다시 실행한다 -
     *     가격이 소급 복구된 직후처럼 "오늘 이미 돌았어도 반드시 다시
     *     돌려야" 하는 경우를 위한 강제 실행(DailyPriceIntegrityService
     *     복구 런북 참고).
     */
    public void runUniverse(boolean force) {
        List<String> domesticUniverse = domesticUniverseSelectionService.selectAndBackfillUniverse();
        List<String> overseasUniverse = overseasUniverseSelectionService.selectAndBackfillUniverse();

        List<String> universe = new ArrayList<>(domesticUniverse.size() + overseasUniverse.size());
        universe.addAll(domesticUniverse);
        universe.addAll(overseasUniverse);

        log.info("유니버스 백테스트 시작: 국내={}종목, 해외={}종목, force={}",
            domesticUniverse.size(), overseasUniverse.size(), force);

        int ranCount = 0;
        int skippedFreshCount = 0;
        int failedCount = 0;
        for (String stockCode : universe) {
            if (!force && isAlreadyBacktestedToday(stockCode)) {
                skippedFreshCount++;
                continue;
            }
            try {
                backtestService.runBacktest(stockCode);
                ranCount++;
            } catch (Exception e) {
                failedCount++;
                log.error("유니버스 백테스트 실패(해당 종목만 스킵): stockCode={}, error={}",
                    stockCode, e.getMessage(), e);
            }
        }

        log.info("유니버스 백테스트 완료: 대상={}종목, 실행={}, 오늘이미실행스킵={}, 실패={}",
            universe.size(), ranCount, skippedFreshCount, failedCount);
    }

    private boolean isAlreadyBacktestedToday(String stockCode) {
        return backtestResultRepository.findTopByStockCodeOrderByBacktestDateDesc(stockCode)
            .map(result -> !result.getBacktestDate().isBefore(LocalDate.now()))
            .orElse(false);
    }
}
