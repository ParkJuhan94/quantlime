package com.quantlime.price.service;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.market.cache.DomesticListedStockCache;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.RegularCloseBackfillResult;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.util.DailyPriceSettlementPolicy;
import com.quantlime.stock.domain.Stock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@code close_price}를 정규장 종가로 전환(2026-09-21, {@code
 * DomesticRegularCloseCaptureScheduler} 참고)하기 전에 저장된 최근 거래일들은
 * 여전히 NXT 포함 값이다 - 이 서비스가 1분봉({@code interval=1m}) API로 각
 * 날짜의 15:30 직전 마지막 체결가를 다시 구해 그 값으로 소급 확정한다.
 * {@code interval=1d} 캔들은 세션 구분이 없어 이 용도로 못 쓴다(토스 API에
 * 정규장만 걸러주는 파라미터가 없음).
 *
 * <p>1회성 마이그레이션이라 자동 스케줄러에는 연결하지 않는다 - {@code
 * DevController}의 수동 트리거로만 실행한다. 이미 {@code
 * regularCloseConfirmed=true}인 행은 건너뛰므로 멱등이다(중간에 끊겨도
 * 재실행하면 이어서 진행됨).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegularCloseBackfillService {

    private static final int DEFAULT_BACKFILL_DAYS = DailyPriceSettlementPolicy.RESETTLEMENT_WINDOW_DAYS;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_CLOSE_TIME = LocalTime.of(15, 30);

    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final DomesticListedStockCache domesticListedStockCache;
    private final TossApiClient tossApiClient;

    public RegularCloseBackfillResult backfill(List<String> stockCodes, Integer days) {
        List<String> targets = (stockCodes == null || stockCodes.isEmpty())
            ? domesticListedStockCache.get().stream().map(Stock::getStockCode).toList()
            : stockCodes;
        int windowDays = days != null ? days : DEFAULT_BACKFILL_DAYS;
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(windowDays);

        log.info("정규장 종가 백필 시작: 대상={}종목, 기간={}~{}", targets.size(), windowStart, today.minusDays(1));

        int confirmed = 0;
        int skipped = 0;
        int failed = 0;
        for (String stockCode : targets) {
            List<DomesticDailyPrice> candidates = domesticDailyPriceRepository
                .findByStockCodeAndTradeDateBetweenOrderByTradeDateDesc(stockCode, windowStart, today.minusDays(1))
                .stream()
                .filter(price -> !price.isRegularCloseConfirmed())
                .toList();

            // 호출 사이 페이싱은 여기서 따로 sleep하지 않는다 - TossApiClient
            // .get1MinuteCandleBefore가 내부적으로 awaitCandleRateLimit()을 거쳐
            // 이미 전역으로 최소 간격을 강제하므로(단일 스레드 순차 호출이라 그
            // 간격이 곧 이 루프의 실제 간격이 된다), 여기서 추가로 sleep하면
            // "네트워크 왕복시간 + 페이싱 간격"이 중복으로 더해져 그만큼
            // 느려지기만 한다(2026-09-22 발견 - 제거로 왕복시간만큼 단축).
            for (DomesticDailyPrice price : candidates) {
                BackfillOutcome outcome = backfillOne(stockCode, price);
                switch (outcome) {
                    case CONFIRMED -> confirmed++;
                    case SKIPPED -> skipped++;
                    case FAILED -> failed++;
                }
            }
        }

        log.info("정규장 종가 백필 완료: 확정={}건, 스킵={}건, 실패={}건", confirmed, skipped, failed);
        return RegularCloseBackfillResult.of(confirmed, skipped, failed);
    }

    private BackfillOutcome backfillOne(String stockCode, DomesticDailyPrice price) {
        // KST +09:00 오프셋 대신 UTC(Z)로 변환해 넘긴다 - TossApiClient
        // .get1MinuteCandleBefore 클라이언트로는 쿼리스트링에 리터럴 +를 보내지도,
        // %2B로 사전 인코딩해 보내지도 못한다(실측 확인, 그 메서드 javadoc 참고).
        // +가 아예 없는 Z 표기를 쓰면 이 문제 자체를 피할 수 있다.
        String before = price.getTradeDate().atTime(REGULAR_CLOSE_TIME)
            .atZone(KST).toInstant().toString();
        try {
            TossCandleResponse response = tossApiClient.get1MinuteCandleBefore(stockCode, before);
            List<TossCandleResponse.TossCandle> candles = response.result().candles();
            if (candles == null || candles.isEmpty()) {
                log.debug("정규장 종가 백필 스킵(1분봉 없음, 거래정지 등): stockCode={}, date={}",
                    stockCode, price.getTradeDate());
                return BackfillOutcome.SKIPPED;
            }
            long regularClose = Long.parseLong(candles.get(0).closePrice());
            price.confirmRegularClose(regularClose);
            domesticDailyPriceRepository.save(price);
            return BackfillOutcome.CONFIRMED;
        } catch (Exception e) {
            log.warn("정규장 종가 백필 실패, 스킵: stockCode={}, date={}, error={}",
                stockCode, price.getTradeDate(), e.getMessage());
            return BackfillOutcome.FAILED;
        }
    }

    private enum BackfillOutcome {
        CONFIRMED, SKIPPED, FAILED
    }
}
