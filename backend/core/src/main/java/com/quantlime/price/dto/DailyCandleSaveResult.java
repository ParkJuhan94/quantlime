package com.quantlime.price.dto;

/**
 * {@code saveNewCandles}(국내/해외 공통)의 결과 - 저장 건수뿐 아니라
 * 수정주가 소급 재조정이 감지됐는지도 함께 반환해, 호출측이 감지 시
 * 전 구간 재백필({@code rebackfillAdjustedHistory})을 트리거할 수 있게 한다.
 */
public record DailyCandleSaveResult(int savedCount, boolean restatementDetected) {

    public static DailyCandleSaveResult of(int savedCount, boolean restatementDetected) {
        return new DailyCandleSaveResult(savedCount, restatementDetected);
    }
}
