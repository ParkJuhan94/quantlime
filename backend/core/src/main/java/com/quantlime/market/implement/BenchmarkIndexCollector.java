package com.quantlime.market.implement;

import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverIndexCandleResponse;
import com.quantlime.market.domain.OverseasIndexCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.IntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 네이버 금융에서 지수 일봉을 가져와 {@code benchmark_index}에 쌓는 구현 레이어
 * (Implementation) - 외부 호출·페이지 루프·딜레이·중복 스킵·저장이라는 "어떻게 가져와
 * 저장하는가"를 이 컴포넌트가 맡고, {@code BenchmarkIndexBackfillService}는 어떤 지수를
 * 언제 갱신할지만 정한다.
 *
 * <p>네이버 금융 비공식 API는 pageSize 상한이 60이지만 page를 늘리며 호출하면 끊김 없이
 * 더 과거로 이어진다(실제 호출로 확인, NaverFinanceApiClient.getIndexPrices 참고) - Toss
 * 캔들의 count/before 커서 페이지네이션과 달리 page 번호 증가 방식이라는 점만 다르고,
 * 구조는 DomesticDailyPriceService.backfillHistoryIfNeeded와 동일하다.
 *
 * <p>해외(나스닥/S&amp;P500)는 홈 화면 지수카드가 쓰는 것과 같은 데이터 소스
 * (OverseasIndexChartCache/NaverFinanceApiClient.getWorldIndexPrices)를 재사용하되, 그쪽은
 * 60초 TTL 캐시일 뿐 영속 저장을 안 해서 백테스트용 영속 이력은 이 컴포넌트가 별도로 쌓는다.
 *
 * <p>종목 일봉(Toss {@code /api/v1/candles})과 달리 이 지수는 NXT가 반영되지 않아 장 마감
 * (15:30) 이후면 그대로 확정값이다(CLAUDE.md §10, §7 "실행 시점" 참고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkIndexCollector {

    // 네이버 금융 비공식 API는 pageSize가 60을 넘으면 400을 반환한다
    // (DomesticIndexChartCache와 동일하게 확인된 제약).
    private static final int PAGE_SIZE = 60;
    private static final long API_DELAY_MS = 200;
    // 정상 종료 조건(짧은 페이지)에 못 미치는 이상 응답이 반복돼도 무한
    // 루프에 빠지지 않도록 하는 안전장치 - 400일 목표엔 page 7개면 충분.
    private static final int MAX_PAGES = 20;

    private final NaverFinanceApiClient naverFinanceApiClient;
    private final BenchmarkIndexReader benchmarkIndexReader;
    private final BenchmarkIndexAppender benchmarkIndexAppender;

    /** 국내 지수 이력을 목표 일수까지 과거로 백필한다 - 이미 충분히 쌓였으면 스킵. */
    public void backfillDomestic(String indexCode, int targetDays) {
        backfill(indexCode, targetDays,
            page -> naverFinanceApiClient.getIndexPrices(indexCode, PAGE_SIZE, page));
    }

    public void backfillWorld(String indexCode, OverseasIndexCode overseasIndexCode, int targetDays) {
        backfill(indexCode, targetDays,
            page -> naverFinanceApiClient.getWorldIndexPrices(overseasIndexCode.getReutersCode(), PAGE_SIZE, page));
    }

    /**
     * 국내 지수의 최신 종가만 갱신한다 - {@link #backfillDomestic}는 "이미 목표치만큼 쌓였으면
     * 스킵"하는 1회성 딥백필이라, 400일치가 이미 있으면 그 뒤로 며칠이 지나든 최신 종가를
     * 영원히 갱신하지 않는다(2026-07-30 실제로 겪은 버그 - KOSPI 벤치마크가 2주 전 날짜에
     * 멈춰 있었고, 그 stale 종가를 {@code MarketIndexCache}가 "전일 종가"로 잘못 사용해
     * 등락률이 완전히 틀어졌다). 항상 최신 페이지(1페이지=최근 60일) 하나만 조회해 신규
     * 거래일만 저장한다.
     */
    public void refreshRecentDomestic(String indexCode) {
        List<NaverIndexCandleResponse> candles = naverFinanceApiClient.getIndexPrices(indexCode, PAGE_SIZE);
        int saved = saveNewCandles(indexCode, candles);
        log.info("국내 지수 벤치마크 최신 갭필 완료: indexCode={}, 신규저장={}건", indexCode, saved);
    }

    /**
     * 해외 버전 - 국내에만 적용됐던 수정을 2026-07-31에 해외(NASDAQ/SP500)에도 마저 적용했다
     * (토스 신규 API 검토 중 실제 DB에서 해외 지수만 8거래일 갭이 벌어진 걸 발견).
     */
    public void refreshRecentWorld(String indexCode, OverseasIndexCode overseasIndexCode) {
        List<NaverIndexCandleResponse> candles =
            naverFinanceApiClient.getWorldIndexPrices(overseasIndexCode.getReutersCode(), PAGE_SIZE);
        int saved = saveNewCandles(indexCode, candles);
        log.info("해외 지수 벤치마크 최신 갭필 완료: indexCode={}, 신규저장={}건", indexCode, saved);
    }

    private void backfill(String indexCode, int targetDays, IntFunction<List<NaverIndexCandleResponse>> fetchPage) {
        long existingCount = benchmarkIndexReader.count(indexCode);
        if (existingCount >= targetDays) {
            log.debug("벤치마크 이력 백필 불필요: indexCode={}, 기존건수={}", indexCode, existingCount);
            return;
        }

        log.info("벤치마크 이력 백필 시작: indexCode={}, 목표={}일, 기존={}건",
            indexCode, targetDays, existingCount);

        int savedCount = 0;
        for (int page = 1; page <= MAX_PAGES && existingCount + savedCount < targetDays; page++) {
            List<NaverIndexCandleResponse> candles = fetchPage.apply(page);
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
     * 백필 루프 전체를 하나의 트랜잭션으로 묶지 않는다(DomesticDailyPriceService.
     * backfillHistoryIfNeeded와 동일한 이유 - 외부 API 왕복·딜레이가 여러
     * 번 있어 저장은 건별로 커밋된다). 같은 클래스 내 self-invocation이라
     * @Transactional을 붙여도 프록시를 안 타 무의미하므로 애초에 두지 않는다.
     */
    private int saveNewCandles(String indexCode, List<NaverIndexCandleResponse> candles) {
        int saved = 0;
        for (NaverIndexCandleResponse candle : candles) {
            LocalDate tradeDate = parseTradeDate(candle.localTradedAt());
            if (benchmarkIndexReader.exists(indexCode, tradeDate)) {
                continue;
            }
            try {
                benchmarkIndexAppender.append(
                    indexCode,
                    tradeDate,
                    parseNumber(candle.openPrice()),
                    parseNumber(candle.highPrice()),
                    parseNumber(candle.lowPrice()),
                    parseNumber(candle.closePrice()));
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

    /**
     * 국내 지수는 localTradedAt이 "2026-07-15" 순수 날짜지만, 해외지수는
     * "2026-07-14T17:15:59-04:00"처럼 타임존 오프셋이 붙은 전체 일시로 온다
     * (OverseasIndexChartCache와 동일하게 확인된 차이) - 순수 날짜 파싱이
     * 실패하면 오프셋 일시로 재시도한다.
     */
    private LocalDate parseTradeDate(String localTradedAt) {
        try {
            return LocalDate.parse(localTradedAt);
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(localTradedAt).toLocalDate();
        }
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
