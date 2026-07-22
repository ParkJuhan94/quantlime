package com.quantlime.market.service;

import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverIndexCandleResponse;
import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 백테스트 초과수익률 계산의 벤치마크 기준선(국내 지수 일별 종가 이력)을
 * 영속 백필한다. 네이버 금융 비공식 API는 pageSize 상한이 60이지만 page를
 * 늘리며 호출하면 끊김 없이 더 과거로 이어진다(실제 호출로 확인,
 * NaverFinanceApiClient.getIndexPrices 참고) - Toss 캔들의 count/before
 * 커서 페이지네이션과 달리 page 번호 증가 방식이라는 점만 다르고, 구조는
 * DailyPriceService.backfillHistoryIfNeeded와 동일하다.
 *
 * <p>OHLCV 수집 배치와 동일하게 장 마감(15:30) 이후 실행을 전제로 한다 -
 * 장중 호출 시 당일 종가가 아직 확정되지 않은 값으로 저장될 수 있다
 * (CLAUDE.md §10 "OHLCV 수집 배치는 장 마감 이후에만 실행" 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkIndexBackfillService {

    private static final List<String> DOMESTIC_INDEX_CODES = List.of("KOSPI", "KOSDAQ");
    private static final int BACKFILL_TARGET_DAYS = 400;
    // 네이버 금융 비공식 API는 pageSize가 60을 넘으면 400을 반환한다
    // (IndexChartCache와 동일하게 확인된 제약).
    private static final int PAGE_SIZE = 60;
    private static final long API_DELAY_MS = 200;
    // 정상 종료 조건(짧은 페이지)에 못 미치는 이상 응답이 반복돼도 무한
    // 루프에 빠지지 않도록 하는 안전장치 - 400일 목표엔 page 7개면 충분.
    private static final int MAX_PAGES = 20;

    private final BenchmarkIndexRepository benchmarkIndexRepository;
    private final NaverFinanceApiClient naverFinanceApiClient;

    public void backfillAllIfNeeded() {
        for (String indexCode : DOMESTIC_INDEX_CODES) {
            backfillIfNeeded(indexCode, BACKFILL_TARGET_DAYS);
        }
    }

    public void backfillIfNeeded(String indexCode, int targetDays) {
        long existingCount = benchmarkIndexRepository.countByIndexCode(indexCode);
        if (existingCount >= targetDays) {
            log.debug("벤치마크 이력 백필 불필요: indexCode={}, 기존건수={}", indexCode, existingCount);
            return;
        }

        log.info("벤치마크 이력 백필 시작: indexCode={}, 목표={}일, 기존={}건",
            indexCode, targetDays, existingCount);

        int savedCount = 0;
        for (int page = 1; page <= MAX_PAGES && existingCount + savedCount < targetDays; page++) {
            List<NaverIndexCandleResponse> candles =
                naverFinanceApiClient.getIndexPrices(indexCode, PAGE_SIZE, page);
            if (candles == null || candles.isEmpty()) {
                break;
            }

            savedCount += saveNewCandles(indexCode, candles);

            boolean lastPage = candles.size() < PAGE_SIZE;
            if (lastPage) {
                break;
            }
            if (!sleepBeforeNextPage(indexCode)) {
                return;
            }
        }

        log.info("벤치마크 이력 백필 완료: indexCode={}, 신규저장={}건", indexCode, savedCount);
    }

    /**
     * 백필 루프 전체를 하나의 트랜잭션으로 묶지 않는다(DailyPriceService.
     * backfillHistoryIfNeeded와 동일한 이유 - 외부 API 왕복·딜레이가 여러
     * 번 있어 저장은 건별로 커밋된다). 같은 클래스 내 self-invocation이라
     * @Transactional을 붙여도 프록시를 안 타 무의미하므로 애초에 두지 않는다.
     */
    private int saveNewCandles(String indexCode, List<NaverIndexCandleResponse> candles) {
        int saved = 0;
        for (NaverIndexCandleResponse candle : candles) {
            LocalDate tradeDate = LocalDate.parse(candle.localTradedAt());
            if (benchmarkIndexRepository.existsByIndexCodeAndTradeDate(indexCode, tradeDate)) {
                continue;
            }
            try {
                benchmarkIndexRepository.save(BenchmarkIndex.of(
                    indexCode,
                    tradeDate,
                    parseNumber(candle.openPrice()),
                    parseNumber(candle.highPrice()),
                    parseNumber(candle.lowPrice()),
                    parseNumber(candle.closePrice())));
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.debug("벤치마크 이력 중복 저장 스킵: indexCode={}, date={}", indexCode, tradeDate);
            }
        }
        return saved;
    }

    private double parseNumber(String raw) {
        return Double.parseDouble(raw.replace(",", ""));
    }

    private boolean sleepBeforeNextPage(String indexCode) {
        try {
            Thread.sleep(API_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("벤치마크 이력 백필 중단: 인터럽트 발생, indexCode={}", indexCode);
            return false;
        }
    }
}
