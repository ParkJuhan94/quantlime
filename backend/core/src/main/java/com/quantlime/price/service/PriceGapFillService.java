package com.quantlime.price.service;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.util.DailyPriceSettlementPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 종목 하나의 마지막 저장일을 확인해, 없으면 깊은 백필로, 있으면 그 다음날
 * 부터 오늘까지의 갭만 최소 호출로 채운다 - 총 건수 기준으로 스킵 여부를
 * 판단하는 기존 백필 서비스({@link DomesticDailyPriceService#backfillHistoryIfNeeded}
 * / {@link OverseasDailyPriceBackfillService#backfillHistoryIfNeeded})는
 * "이미 충분히 쌓여 있으면" 스킵하므로, 이미 200~400일치가 있는 종목이
 * 서버 다운타임 동안 최근 며칠만 비어도 놓친다 - 이 서비스는 그 최근
 * 결측 구간만 정확히 겨냥한다(MarketDataRefreshService가 사용).
 *
 * <p>국내/해외 모두 이제 같은 Toss 캔들 API를 쓰므로(2026-07-29, 해외는
 * KIS에서 이관) 갭 계산 로직이 완전히 대칭이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceGapFillService {

    // 토스 캔들 조회 count 파라미터 상한(DomesticDailyPriceService.BACKFILL_PAGE_SIZE와
    // 동일 값) - 갭이 이보다 크면 단일 호출로 못 채우므로 깊은 백필로 폴백한다.
    // 해외도 이제 같은 Toss 캔들 API를 쓰므로(2026-07-29, KIS 이관) 국내와
    // 값이 같아져 상수 하나로 통합했다.
    private static final int SINGLE_CALL_CAP_DAYS = 200;
    private static final int DEEP_BACKFILL_TARGET_DAYS = 200;
    // 갭은 거래일이 아니라 달력일로 계산하므로(휴장일 포함), 주말+연휴를
    // 감안해 약간의 여유를 둔다 - 부족하게 요청해 하루라도 놓치는 것보다
    // 여유분 며칠을 더 조회하는 편이 안전하다(중복은 존재 여부 체크로 스킵됨).
    private static final int GAP_BUFFER_DAYS = 5;

    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final DomesticDailyPriceService domesticDailyPriceService;
    private final OverseasDailyPriceBackfillService overseasDailyPriceBackfillService;

    /**
     * @return 실제로 외부 API 호출이 발생했는지 여부 - 호출측(MarketDataRefreshService)이
     * 종목 간 레이트리밋 딜레이를 API를 실제로 부른 경우에만 주도록 판단하는 데 쓴다
     * (이미 최신이라 스킵된 종목까지 매번 딜레이를 걸면 전종목 스윕이 불필요하게 느려진다).
     */
    public boolean fillDomesticGap(String stockCode) {
        Optional<DomesticDailyPrice> latest = domesticDailyPriceRepository
            .findTopByStockCodeOrderByTradeDateDesc(stockCode);
        if (latest.isEmpty()) {
            domesticDailyPriceService.backfillHistoryIfNeeded(stockCode, DEEP_BACKFILL_TARGET_DAYS);
            return true;
        }

        // "갭이 없다(오늘 행이 있다)"와 "확정됐다(NXT 애프터마켓 종료인 20:00
        // 이후에 저장됐다)"는 전혀 다른 조건이다 - 이 둘을 같은 것으로 취급한
        // 게(구 rawGapDays<=0 조기 반환) 장중 스냅샷이 그날의 확정 종가로
        // 영구 고정되는 버그의 원인이었다(DailyPriceSettlementPolicy 참고,
        // 실측: 삼성전자 2026-07-30 08:23 프리마켓 조각이 그렇게 고정됨).
        // tradeDate==today 조건은 유지해야 한다 - "확정이면 무조건 스킵"으로
        // 만들면 예를 들어 금요일 확정분을 들고 있는 채로 월요일을 맞았을 때
        // 다음 거래일 데이터를 영영 못 받는다.
        DomesticDailyPrice latestPrice = latest.get();
        if (isSettledToday(latestPrice) || isRecentlyRefreshedToday(latestPrice)) {
            log.debug("가격 재확정 불필요(오늘 확정분 보유 또는 최근 갱신됨): stockCode={}, 최신저장일={}",
                stockCode, latestPrice.getTradeDate());
            return false;
        }

        long rawGapDays = ChronoUnit.DAYS.between(latestPrice.getTradeDate(), LocalDate.now());
        int lookbackDays = (int) rawGapDays + GAP_BUFFER_DAYS;
        if (lookbackDays > SINGLE_CALL_CAP_DAYS) {
            log.info("가격 갭이 단일 호출 한도 초과, 깊은 백필로 대체: stockCode={}, 갭={}일",
                stockCode, rawGapDays);
            domesticDailyPriceService.backfillHistoryIfNeeded(stockCode, DEEP_BACKFILL_TARGET_DAYS);
            return true;
        }
        domesticDailyPriceService.refreshRecent(stockCode, lookbackDays);
        return true;
    }

    private boolean isSettledToday(DomesticDailyPrice latest) {
        LocalDate today = LocalDate.now();
        return latest.getTradeDate().isEqual(today)
            && DailyPriceSettlementPolicy.isSettled(today, latest.getUpdatedAt());
    }

    /** 미확정이더라도 최근에 이미 재조회했다면 이번 실행에서는 다시 부르지 않는다(재기동 가드). */
    private boolean isRecentlyRefreshedToday(DomesticDailyPrice latest) {
        return latest.getTradeDate().isEqual(LocalDate.now())
            && DailyPriceSettlementPolicy.isRecentlyRefreshed(latest.getUpdatedAt(), LocalDateTime.now());
    }

    public boolean fillOverseasGap(String stockCode) {
        Optional<LocalDate> latestTradeDate = overseasDailyPriceRepository
            .findTopByStockCodeOrderByTradeDateDesc(stockCode)
            .map(OverseasDailyPrice::getTradeDate);
        if (latestTradeDate.isEmpty()) {
            overseasDailyPriceBackfillService.backfillHistoryIfNeeded(stockCode, DEEP_BACKFILL_TARGET_DAYS);
            return true;
        }

        long rawGapDays = ChronoUnit.DAYS.between(latestTradeDate.get(), LocalDate.now());
        if (rawGapDays <= 0) {
            log.debug("해외 가격 갭 없음(이미 최신): stockCode={}, 최신저장일={}", stockCode, latestTradeDate.get());
            return false;
        }
        int lookbackDays = (int) rawGapDays + GAP_BUFFER_DAYS;
        if (lookbackDays > SINGLE_CALL_CAP_DAYS) {
            log.info("해외 가격 갭이 단일 호출 한도 초과, 깊은 백필로 대체: stockCode={}, 갭={}일",
                stockCode, rawGapDays);
            overseasDailyPriceBackfillService.backfillHistoryIfNeeded(stockCode, DEEP_BACKFILL_TARGET_DAYS);
            return true;
        }
        overseasDailyPriceBackfillService.refreshRecent(stockCode, lookbackDays);
        return true;
    }
}
