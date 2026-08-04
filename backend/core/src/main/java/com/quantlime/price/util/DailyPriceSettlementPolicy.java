package com.quantlime.price.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 국내 일봉(Toss {@code /api/v1/candles?interval=1d})이 NXT 프리마켓
 * (08:00~09:00)/애프터마켓(15:30~20:00)을 포함해 20:00까지 계속 갱신된다는
 * 사실에서 나온 확정 판정 정책. 16:00 배치가 저장한 값은 정의상 미확정
 * 스냅샷이고, 최근 거래일 구간은 이미 저장돼 있어도 항상 재조회해 덮어써야
 * 한다(재확정 윈도우) - 이 김에 액면분할/병합 같은 수정주가 소급 재조정도
 * 같은 재조회 경로에서 감지한다({@link #isRestatement}).
 *
 * <p>실측 근거: 2026-07-30 08:23(정규장 개장 전)에 삼성전자 당일 캔들이
 * 거래량 3,856,752와 함께 이미 존재했다(같은 주 다른 거래일은 30~80M) -
 * {@code PriceGapFillService}의 "오늘 행이 있으면 API를 호출조차 안 하는"
 * 조기 반환 때문에 이 프리마켓 스냅샷이 그날의 확정 종가로 영구 고정됐었다.
 */
public final class DailyPriceSettlementPolicy {

    public static final LocalTime SETTLEMENT_TIME = LocalTime.of(20, 0);

    // 재확정 윈도우(달력일). 이 범위 안의 거래일 행은 이미 있어도 항상 최신
    // 응답으로 덮어쓴다. 서버 다운타임이 이보다 길면 자동 복구가 안 되지만
    // (수동 /dev/prices/resettle 필요), 비용은 count 파라미터 증가뿐(호출
    // 수는 동일하고 값이 같으면 save 자체를 생략)이라 넉넉히 잡아도 사실상
    // 공짜다.
    public static final int RESETTLEMENT_WINDOW_DAYS = 20;

    // refreshRecent(stockCode, lookbackDays)의 lookbackDays는 실제로는
    // 캔들 개수(count)로 그대로 넘어간다(DomesticDailyPriceService ->
    // TossApiClient.getDailyCandles). 캔들 20개 ≈ 달력 28일(주말 포함)이라
    // 위 윈도우를 항상 포함한다.
    public static final int MIN_LOOKBACK_CANDLES = 20;

    // 국내 가격제한폭은 정확히 ±30%라 임계값을 30%에 그대로 걸면 상한가/
    // 하한가를 정확히 찍은 "정상" 거래일까지 재조정으로 오탐한다(2026-08-03
    // 실제 복구 런북 실행 중 발견 - threshold=0.30으로 스캔했더니 421건 중
    // 326건이 ratio 1.30~1.32 구간에 몰려 있었고, 이는 액면분할이 아니라
    // 상한가를 정확히 찍은 정상 거래일이었다). 진짜 재조정 후보(주로 정수배)는
    // 1.336 이하와 1.356 이상 사이에 자연스러운 공백이 있어, 그 사이인 0.35를
    // 30% 경계에서 충분히 띄운 임계값으로 채택 - 재검증 결과 421건/328종목
    // →54건/43종목으로 정확히 걸러졌다. 오탐 비용(그 종목 1개 재백필,
    // 2~3콜)이 싸므로 여전히 다소 보수적으로(낮게) 잡는다 - 미탐 비용
    // (백테스트에 가짜 수익률이 남는 것)이 훨씬 크다.
    public static final double DOMESTIC_RESTATEMENT_THRESHOLD = 0.35;
    // 미국은 가격제한폭이 없어 임계값을 더 넉넉히 잡는다.
    public static final double OVERSEAS_RESTATEMENT_THRESHOLD = 0.50;

    // 미확정 행이라도 마지막 갱신 후 이 시간 안이면 재조회를 건너뛴다 -
    // 개발 중 잦은 재기동마다 전종목 스윕(~22분)이 반복되는 것을 막는다.
    public static final Duration PROVISIONAL_REFRESH_MIN_INTERVAL = Duration.ofMinutes(60);

    private DailyPriceSettlementPolicy() {
    }

    public static LocalDate windowStart(LocalDate today) {
        return today.minusDays(RESETTLEMENT_WINDOW_DAYS);
    }

    public static boolean isWithinWindow(LocalDate tradeDate, LocalDate today) {
        return !tradeDate.isBefore(windowStart(today));
    }

    /**
     * updatedAt이 null이면(아직 영속화되지 않은 엔티티 등) 미확정으로
     * 본다 - 단위 테스트에서의 NPE를 막고, 판단이 애매한 쪽을 안전한
     * 방향(재조회)으로 취급한다.
     */
    public static boolean isSettled(LocalDate tradeDate, LocalDateTime updatedAt) {
        if (updatedAt == null) {
            return false;
        }
        LocalDateTime settlementInstant = LocalDateTime.of(tradeDate, SETTLEMENT_TIME);
        return !updatedAt.isBefore(settlementInstant);
    }

    /**
     * 아직 미확정이더라도 최근에 이미 재조회했다면 이번만큼은 다시 부르지
     * 않는다({@link #PROVISIONAL_REFRESH_MIN_INTERVAL} 가드).
     */
    public static boolean isRecentlyRefreshed(LocalDateTime updatedAt, LocalDateTime now) {
        if (updatedAt == null) {
            return false;
        }
        return Duration.between(updatedAt, now).compareTo(PROVISIONAL_REFRESH_MIN_INTERVAL) < 0;
    }

    public static boolean isRestatement(double storedClose, double fetchedClose, double threshold) {
        if (storedClose == 0) {
            return false;
        }
        double ratio = Math.abs(fetchedClose / storedClose - 1.0);
        return ratio >= threshold;
    }
}
