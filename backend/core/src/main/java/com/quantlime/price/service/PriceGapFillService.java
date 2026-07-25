package com.quantlime.price.service;

import com.quantlime.price.domain.DailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 종목 하나의 마지막 저장일을 확인해, 없으면 깊은 백필로, 있으면 그 다음날
 * 부터 오늘까지의 갭만 최소 호출로 채운다 - 총 건수 기준으로 스킵 여부를
 * 판단하는 기존 백필 서비스({@link DailyPriceService#backfillHistoryIfNeeded}
 * / {@link OverseasDailyPriceBackfillService#backfillHistoryIfNeeded})는
 * "이미 충분히 쌓여 있으면" 스킵하므로, 이미 200~400일치가 있는 종목이
 * 서버 다운타임 동안 최근 며칠만 비어도 놓친다 - 이 서비스는 그 최근
 * 결측 구간만 정확히 겨냥한다(MarketDataRefreshService가 사용).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceGapFillService {

    // 토스 캔들 조회 count 파라미터 상한(DailyPriceService.BACKFILL_PAGE_SIZE와
    // 동일 값) - 갭이 이보다 크면 단일 호출로 못 채우므로 깊은 백필로 폴백한다.
    private static final int DOMESTIC_SINGLE_CALL_CAP_DAYS = 200;
    // KIS는 count 파라미터가 없어 baseDate 없이 호출하면 항상 최근 100거래일이
    // 내려온다(OverseasDailyPriceBackfillService 참고) - 갭이 이 범위를
    // 넘으면(장기 다운타임 등) 페이지네이션 백필로 폴백해야 한다.
    private static final int OVERSEAS_SINGLE_CALL_CAP_DAYS = 100;
    private static final int DEEP_BACKFILL_TARGET_DAYS = 200;
    // 갭은 거래일이 아니라 달력일로 계산하므로(휴장일 포함), 주말+연휴를
    // 감안해 약간의 여유를 둔다 - 부족하게 요청해 하루라도 놓치는 것보다
    // 여유분 며칠을 더 조회하는 편이 안전하다(중복은 존재 여부 체크로 스킵됨).
    private static final int GAP_BUFFER_DAYS = 5;

    private final DailyPriceRepository dailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final DailyPriceService dailyPriceService;
    private final OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    /**
     * @return 실제로 외부 API 호출이 발생했는지 여부 - 호출측(MarketDataRefreshService)이
     * 종목 간 레이트리밋 딜레이를 API를 실제로 부른 경우에만 주도록 판단하는 데 쓴다
     * (이미 최신이라 스킵된 종목까지 매번 딜레이를 걸면 전종목 스윕이 불필요하게 느려진다).
     */
    public boolean fillDomesticGap(String stockCode) {
        Optional<LocalDate> latestTradeDate = dailyPriceRepository
            .findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(DailyPrice::getTradeDate);
        if (latestTradeDate.isEmpty()) {
            dailyPriceService.backfillHistoryIfNeeded(stockCode, DEEP_BACKFILL_TARGET_DAYS);
            return true;
        }

        long rawGapDays = ChronoUnit.DAYS.between(latestTradeDate.get(), LocalDate.now());
        if (rawGapDays <= 0) {
            log.debug("가격 갭 없음(이미 최신): stockCode={}, 최신저장일={}", stockCode, latestTradeDate.get());
            return false;
        }
        int lookbackDays = (int) rawGapDays + GAP_BUFFER_DAYS;
        if (lookbackDays > DOMESTIC_SINGLE_CALL_CAP_DAYS) {
            log.info("가격 갭이 단일 호출 한도 초과, 깊은 백필로 대체: stockCode={}, 갭={}일",
                stockCode, rawGapDays);
            dailyPriceService.backfillHistoryIfNeeded(stockCode, DEEP_BACKFILL_TARGET_DAYS);
            return true;
        }
        dailyPriceService.collectDailyPrice(stockCode, lookbackDays);
        return true;
    }

    public boolean fillOverseasGap(String stockCode, String exchangeCode) {
        Optional<LocalDate> latestTradeDate = overseasDailyPriceRepository
            .findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(OverseasDailyPrice::getTradeDate);
        if (latestTradeDate.isEmpty()) {
            overseasDailyPriceBackfillService.backfillHistoryIfNeeded(
                stockCode, exchangeCode, DEEP_BACKFILL_TARGET_DAYS);
            return true;
        }

        long rawGapDays = ChronoUnit.DAYS.between(latestTradeDate.get(), LocalDate.now());
        if (rawGapDays <= 0) {
            log.debug("해외 가격 갭 없음(이미 최신): stockCode={}, 최신저장일={}", stockCode, latestTradeDate.get());
            return false;
        }
        if (rawGapDays + GAP_BUFFER_DAYS > OVERSEAS_SINGLE_CALL_CAP_DAYS) {
            log.info("해외 가격 갭이 단일 호출 한도 초과, 깊은 백필로 대체: stockCode={}, 갭={}일",
                stockCode, rawGapDays);
            overseasDailyPriceBackfillService.backfillHistoryIfNeeded(
                stockCode, exchangeCode, DEEP_BACKFILL_TARGET_DAYS);
            return true;
        }
        overseasDailyPriceBackfillService.collectRecentPrices(stockCode, exchangeCode);
        return true;
    }
}
