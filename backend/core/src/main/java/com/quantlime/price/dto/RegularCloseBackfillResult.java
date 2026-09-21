package com.quantlime.price.dto;

/**
 * {@code RegularCloseBackfillService}의 1회 실행 결과 - 확정/스킵(봉 없음)/
 * 실패(API 오류 등) 건수를 구분해 반환한다.
 */
public record RegularCloseBackfillResult(int confirmed, int skipped, int failed) {

    public static RegularCloseBackfillResult of(int confirmed, int skipped, int failed) {
        return new RegularCloseBackfillResult(confirmed, skipped, failed);
    }
}
