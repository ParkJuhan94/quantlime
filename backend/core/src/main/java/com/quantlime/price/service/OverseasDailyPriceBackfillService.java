package com.quantlime.price.service;

import com.quantlime.common.util.SleepUtil;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.infra.toss.dto.TossPriceMapper;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 해외주식 일별 OHLCV 백필. 원래 KIS(한국투자증권) 기간별시세 API를 썼으나,
 * Toss `/api/v1/candles`가 처음부터 해외 티커를 지원한다는 게 확인되면서
 * (2026-07-29 세션, `toss-openapi.json` 교체 계기로 라이브 재검증) KIS
 * 연동을 걷어내고 국내와 동일한 count/before 커서 페이지네이션으로
 * 통일했다 - 구조는 {@link DomesticDailyPriceService}의 백필과 완전히 동일하고,
 * 차이는 저장 대상이 {@link com.quantlime.price.domain.OverseasDailyPrice}
 * (Double 가격)라는 점뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasDailyPriceBackfillService {

    private static final int BACKFILL_PAGE_SIZE = 200;
    private static final long API_DELAY_MS = 150;

    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final TossApiClient tossApiClient;

    /**
     * lookbackDays는 호출측({@link PriceGapFillService})이 "마지막 저장일부터
     * 오늘까지의 실제 갭"만큼만 지정한다(DomesticDailyPriceService.refreshRecent와
     * 동일한 의도).
     */
    public void refreshRecent(String stockCode, int lookbackDays) {
        TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, lookbackDays, null);
        List<TossCandleResponse.TossCandle> candles = response.result().candles();
        if (candles == null || candles.isEmpty()) {
            log.warn("해외 시세 데이터 없음: stockCode={}", stockCode);
            return;
        }

        int savedCount = saveNewCandles(stockCode, candles);
        if (savedCount > 0) {
            log.info("해외 일별 시세 수집 완료: stockCode={}, 신규저장={}건", stockCode, savedCount);
        } else {
            log.debug("이미 수집된 해외 시세: stockCode={}", stockCode);
        }
    }

    /**
     * 외부 API 왕복과 딜레이가 여러 번 발생할 수 있어 전체를 하나의 트랜잭션으로
     * 묶지 않는다(DomesticDailyPriceService.backfillHistoryIfNeeded와 동일한 이유).
     */
    public void backfillHistoryIfNeeded(String stockCode, int targetDays) {
        long existingCount = overseasDailyPriceRepository.countByStockCode(stockCode);
        if (existingCount >= targetDays) {
            log.debug("해외 이력 백필 불필요: stockCode={}, 기존건수={}", stockCode, existingCount);
            return;
        }

        log.info("해외 이력 백필 시작: stockCode={}, 목표={}일, 기존={}건",
            stockCode, targetDays, existingCount);

        String cursor = null;
        int savedCount = 0;

        while (savedCount < targetDays) {
            TossCandleResponse response = tossApiClient.getDailyCandles(stockCode, BACKFILL_PAGE_SIZE, cursor);
            List<TossCandleResponse.TossCandle> candles = response.result().candles();
            if (candles == null || candles.isEmpty()) {
                break;
            }

            savedCount += saveNewCandles(stockCode, candles);

            boolean noMoreHistory = candles.size() < BACKFILL_PAGE_SIZE
                || response.result().nextBefore() == null;
            if (noMoreHistory) {
                break;
            }
            cursor = response.result().nextBefore();

            if (!SleepUtil.sleepMillis(API_DELAY_MS)) {
                log.warn("해외 이력 백필 중단: 인터럽트 발생, stockCode={}", stockCode);
                return;
            }
        }

        log.info("해외 이력 백필 완료: stockCode={}, 신규저장={}건", stockCode, savedCount);
    }

    private int saveNewCandles(String stockCode, List<TossCandleResponse.TossCandle> candles) {
        int saved = 0;
        for (TossCandleResponse.TossCandle candle : candles) {
            LocalDate tradeDate = TossPriceMapper.toLocalDate(candle.timestamp());
            if (overseasDailyPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) {
                continue;
            }
            try {
                overseasDailyPriceRepository.save(TossPriceMapper.toOverseasDailyPrice(stockCode, candle));
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.debug("해외 이력 백필 중복 저장 스킵: stockCode={}, date={}", stockCode, tradeDate);
            }
        }
        return saved;
    }
}
