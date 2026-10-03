package com.quantlime.price.implement;

import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossCandleResponse;
import com.quantlime.price.domain.DomesticDailyPrice;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 정규장(15:30) 종가를 Toss 1분봉으로 복원해 {@code domestic_daily_price}에 확정하는 구현
 * 레이어(Implementation) - 외부 호출·변환·저장을 이 컴포넌트가 맡고,
 * {@code RegularCloseBackfillService}는 대상 종목·기간 선정과 결과 집계만 한다.
 *
 * <p>호출 사이 페이싱은 여기서 따로 sleep하지 않는다 - {@code TossApiClient
 * .get1MinuteCandleBefore}가 내부적으로 awaitCandleRateLimit()을 거쳐 이미 전역으로 최소
 * 간격을 강제하므로 여기서 추가로 sleep하면 "네트워크 왕복시간 + 페이싱 간격"이 중복으로
 * 더해져 느려지기만 한다(2026-09-22 발견 - 제거로 왕복시간만큼 단축).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegularCloseCollector {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_CLOSE_TIME = LocalTime.of(15, 30);

    private final DailyPriceAppender dailyPriceAppender;
    private final TossApiClient tossApiClient;

    /** 거래일 하나의 정규장 종가를 1분봉으로 복원해 확정한다 - 예외는 삼켜 {@link Outcome#FAILED}로 돌려준다. */
    public Outcome backfillOne(String stockCode, DomesticDailyPrice price) {
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
                return Outcome.SKIPPED;
            }
            long regularClose = Long.parseLong(candles.get(0).closePrice());
            price.confirmRegularClose(regularClose);
            dailyPriceAppender.saveDomestic(price);
            return Outcome.CONFIRMED;
        } catch (Exception e) {
            log.warn("정규장 종가 백필 실패, 스킵: stockCode={}, date={}, error={}",
                stockCode, price.getTradeDate(), e.getMessage());
            return Outcome.FAILED;
        }
    }

    public enum Outcome {
        CONFIRMED, SKIPPED, FAILED
    }
}
