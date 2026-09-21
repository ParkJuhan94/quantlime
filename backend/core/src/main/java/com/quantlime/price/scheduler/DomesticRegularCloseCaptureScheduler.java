package com.quantlime.price.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.market.cache.DomesticListedStockCache;
import com.quantlime.price.cache.DomesticMarketCalendarCache;
import com.quantlime.price.cache.PriceCacheStore;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.dto.response.PriceSnapshot;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.stock.domain.Stock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정규장 마감(15:30) 직후, 그 시점 {@link PriceCacheStore} 실시간 시세 스냅샷을
 * {@code domestic_daily_price.close_price}에 직접 확정 저장한다({@link
 * DomesticDailyPrice#confirmRegularClose}).
 *
 * <p>토스 {@code /api/v1/candles?interval=1d}는 NXT 프리/애프터마켓까지 반영해
 * 20:00까지 계속 갱신되는 값이라, 정규장 마감(다른 증권사 앱이 쓰는 관례) 기준
 * 종가를 얻으려면 그 값을 그대로 쓸 수 없다 - {@code DomesticMarketPriceSweepScheduler}가
 * 100ms마다 이미 채워둔 Redis 스냅샷을 재활용하므로 별도 Toss 호출은 없다.
 * 이렇게 확정한 종가는 {@code DomesticDailyPriceService#upsertCandle}이 이후
 * 16:00/20:10 일봉 배치에서 NXT 포함 값으로 덮어쓰지 않도록 보호한다(시가/
 * 고가/저가/거래량은 그 배치가 그대로 채운다) - 별도 테이블
 * ({@code domestic_regular_close_price})로 분리했다가 두 테이블 간 신선도
 * 불일치로 전일종가가 몇 주씩 밀리는 버그가 있어(2026-09-21 발견) 같은 행에
 * 직접 합쳤다. 상세 배경은 {@code ~/.claude/plans/dynamic-prancing-lerdorf.md} 참고.
 *
 * <p>고정 cron(15:35)이라 조기폐장 등 정규장 종료 시각이 다른 특수한 날은
 * {@link PriceCacheStore}의 TTL(5분)이 이미 지나 스냅샷을 못 찾을 수 있다 -
 * 그런 종목/날짜는 이번 캡처를 건너뛰고, 이후 배치가 NXT 포함 종가로 그대로
 * 채운다(그날 하루만 기존 동작으로 되돌아갈 뿐 이후 정상화됨).
 *
 * <p><b>기동 시 캐치업({@link #captureIfWithinStartupSafeWindow}, {@code
 * StartupCatchUpRunner}에서 호출)은 반드시 15:35~{@value
 * #STARTUP_CATCHUP_GRACE_MINUTES}분 이내의 좁은 시간대에서만 캡처를
 * 시도한다</b> - 로컬 개발처럼 서버가 항상 떠있지 않은 환경에서 배포/재기동이
 * 15:35를 지나 이뤄지면 그날 정규 cron이 아예 발동하지 못하는데, 이때 시간대
 * 제한 없이 그냥 "지금 Redis 값"을 캡처하면 위험하다 - NXT 애프터마켓(15:40~)
 * 오픈 이후는 스윕이 계속 값을 드리프트시키고 있어, 창을 넘겨서 캡처하면 이미
 * 드리프트된 애프터마켓 가격을 "정규장 종가"로 확정 저장하게 되고, 그러면
 * 이후 배치가 그 틀린 값을 보호 대상으로 착각해 그대로 유지하게 돼 이 클래스가
 * 애초에 막으려던 문제를 스스로 재현한다(2026-08-17 실사용 중 발견, 구
 * domestic_regular_close_price 시절). 그레이스는 15:39까지만(15:40 NXT 오픈과
 * 1분 여유) - 창을 넘긴 날짜/종목은 캡처를 포기하고 기존 폴백(NXT 포함 일봉
 * 종가)에 맡긴다. 이 프로젝트는 분봉을 저장하지 않아 그 순간이 지나면 사후에
 * 복구할 방법이 없기 때문에, 그 시각을 넘기면 정확도보다 안전(틀린 값을
 * "확정"으로 잘못 저장하지 않는 것)을 우선한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomesticRegularCloseCaptureScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_CLOSE_TIME = LocalTime.of(15, 35);
    private static final int STARTUP_CATCHUP_GRACE_MINUTES = 4;

    private final DomesticMarketCalendarCache domesticMarketCalendarCache;
    private final DomesticListedStockCache domesticListedStockCache;
    private final PriceCacheStore priceCacheStore;
    private final DomesticDailyPriceRepository domesticDailyPriceRepository;

    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Seoul")
    public void captureRegularClose() {
        SafeExecutor.runSafely("정규장 종가 캡처", this::doCapture);
    }

    /**
     * 기동 시 캐치업 전용 진입점 - {@code StartupCatchUpRunner}가 호출한다.
     * 클래스 javadoc 참고: 15:35~15:39(KST) 안전 시간대를 벗어나면 캡처를
     * 시도하지 않고 조용히 넘어간다(그 날은 기존 폴백에 맡김).
     */
    public void captureIfWithinStartupSafeWindow() {
        captureIfWithinStartupSafeWindow(LocalTime.now(KST));
    }

    // now를 파라미터로 받는 오버로드는 테스트에서 시간대 경계를 직접 통제하기 위함
    void captureIfWithinStartupSafeWindow(LocalTime now) {
        LocalTime windowEnd = REGULAR_CLOSE_TIME.plus(Duration.ofMinutes(STARTUP_CATCHUP_GRACE_MINUTES));
        if (now.isBefore(REGULAR_CLOSE_TIME) || now.isAfter(windowEnd)) {
            log.debug("정규장 종가 캡처 기동 캐치업 스킵: 안전 시간대(15:35~15:39) 밖 - now={}", now);
            return;
        }
        doCapture();
    }

    /**
     * 종목당 개별 Redis GET + DB 조회/save(왕복 3회 × ~2,700종목)를 왕복
     * 3회로 줄인다(2026-09 성능 감사, domestic_daily_price 직접 저장 전환
     * 이후에도 동일한 구조 유지) - Redis 스냅샷 배치 조회(파이프라인), 오늘 행
     * 배치 조회, 업데이트/신규삽입으로 나눠 각각 saveAll. {@link
     * PriceCacheStore} TTL이 5분이라 종목별 순차 루프가 그 시간을 넘기면 후반
     * 종목이 캐시 미스로 캡처를 놓치는 문제도 같이 해결한다(배치 조회는 한 번의
     * 왕복이라 5분을 넘길 일이 없다).
     */
    private void doCapture() {
        if (!domesticMarketCalendarCache.isTradingDayToday()) {
            log.debug("정규장 종가 캡처 스킵: 휴장일");
            return;
        }

        LocalDate today = LocalDate.now();
        List<Stock> stocks = domesticListedStockCache.get();
        List<String> stockCodes = stocks.stream().map(Stock::getStockCode).toList();

        Map<String, DomesticDailyPrice> todaysRowByCode = domesticDailyPriceRepository
            .findByStockCodeInAndTradeDate(stockCodes, today).stream()
            .collect(Collectors.toMap(DomesticDailyPrice::getStockCode, Function.identity()));
        Map<String, PriceSnapshot> snapshotByCode = priceCacheStore.findAll(stockCodes);

        List<DomesticDailyPrice> toUpdate = new ArrayList<>();
        List<DomesticDailyPrice> toInsert = new ArrayList<>();
        for (String stockCode : stockCodes) {
            DomesticDailyPrice existing = todaysRowByCode.get(stockCode);
            if (existing != null && existing.isRegularCloseConfirmed()) {
                continue; // 이미 캡처됨(재기동 등으로 중복 트리거된 경우) - 멱등 처리
            }
            PriceSnapshot snapshot = snapshotByCode.get(stockCode);
            if (snapshot == null || snapshot.currentPrice() == null) {
                continue;
            }
            long closePrice = Math.round(snapshot.currentPrice());
            if (existing != null) {
                existing.confirmRegularClose(closePrice);
                toUpdate.add(existing);
            } else {
                toInsert.add(DomesticDailyPrice.ofRegularCloseOnly(stockCode, today, closePrice));
            }
        }

        int captured = saveUpdates(toUpdate) + saveInsertsOrFallbackToIndividual(toInsert);
        log.info("정규장 종가 캡처 완료: 대상={}종목, 캡처={}건", stocks.size(), captured);
    }

    private int saveUpdates(List<DomesticDailyPrice> toUpdate) {
        if (toUpdate.isEmpty()) {
            return 0;
        }
        domesticDailyPriceRepository.saveAll(toUpdate);
        return toUpdate.size();
    }

    /**
     * 정상 경로는 saveAll 한 번(왕복 1회)이지만, 정규 cron과 기동 캐치업이
     * 좁은 창 안에서 동시에 돈 경우(클래스 javadoc 참고) 같은 종목의 오늘 행을
     * 두 실행이 동시에 신규 삽입하려다 유니크 제약 충돌이 날 수 있다 - saveAll은
     * 한 트랜잭션이라 그런 충돌 하나가 배치 전체를 롤백시킨다. 그 경우에만
     * 종목별 save로 폴백해 충돌난 종목만 건너뛰고 나머지는 살린다(드문 경로라
     * 이 폴백에서는 왕복 수를 아끼지 않는다). 업데이트 대상(toUpdate)은 이미
     * 존재하는 PK를 갱신하는 것이라 이런 충돌이 없어 별도 폴백을 두지 않는다.
     */
    private int saveInsertsOrFallbackToIndividual(List<DomesticDailyPrice> toInsert) {
        if (toInsert.isEmpty()) {
            return 0;
        }
        try {
            domesticDailyPriceRepository.saveAll(toInsert);
            return toInsert.size();
        } catch (DataIntegrityViolationException e) {
            log.debug("정규장 종가 캡처 신규 행 일괄 저장 충돌 - 종목별 저장으로 폴백: 대상={}건", toInsert.size());
            int captured = 0;
            for (DomesticDailyPrice candidate : toInsert) {
                try {
                    domesticDailyPriceRepository.save(candidate);
                    captured++;
                } catch (DataIntegrityViolationException individual) {
                    // 동시에 다른 실행이 먼저 오늘 행을 만들었다는 뜻 - 그 값을
                    // 존중하고 이번엔 건너뛴다. 놓친 종목은 다음 캡처 기회에
                    // 다시 시도된다.
                    log.debug("정규장 종가 캡처 동시 저장 충돌 스킵: stockCode={}", candidate.getStockCode());
                }
            }
            return captured;
        }
    }
}
