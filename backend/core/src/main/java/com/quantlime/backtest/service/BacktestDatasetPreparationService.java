package com.quantlime.backtest.service;

import com.quantlime.market.service.BenchmarkIndexBackfillService;
import com.quantlime.market.service.DomesticUniverseSelectionService;
import com.quantlime.market.service.OverseasUniverseSelectionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 트리거2(백테스트 데이터 준비) - 국내/해외 유니버스 선정+백필과 벤치마크
 * 지수(국내 KOSPI/KOSDAQ + 해외 NASDAQ/SP500) 백필을 한 번에 묶는다.
 * 세 서비스 모두 기존 로직을 그대로 재사용만 하고(총 건수 기준 깊은
 * 백필 - 트리거1의 갭 기반 로직과 달리 여기서는 "처음부터 400일을 채운다"는
 * 목적에 count-based 스킵이 그대로 적합), 종목마스터 동기화는 포함하지
 * 않는다(국내는 이미 주 1회 자동 스케줄러, 해외는 수동 트리거가 별도로
 * 담당 - 관심사 분리).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestDatasetPreparationService {

    private final DomesticUniverseSelectionService domesticUniverseSelectionService;
    private final OverseasUniverseSelectionService overseasUniverseSelectionService;
    private final BenchmarkIndexBackfillService benchmarkIndexBackfillService;

    public void prepareDataset() {
        List<String> domesticUniverse = domesticUniverseSelectionService.selectAndBackfillUniverse();
        List<String> overseasUniverse = overseasUniverseSelectionService.selectAndBackfillUniverse();
        benchmarkIndexBackfillService.backfillAllIfNeeded();

        log.info("백테스트 데이터셋 준비 완료: 국내 유니버스={}종목, 해외 유니버스={}종목",
            domesticUniverse.size(), overseasUniverse.size());
    }
}
