package com.quantlime.infra.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GET /api/v1/rankings 응답. 랭킹이 집계되지 않은 조합(예: 장 시작
 * 직후)은 에러가 아니라 {@code rankings}가 빈 배열, {@code rankedAt}이
 * null로 내려온다. 응답 항목 수는 요청한 {@code count}보다 적을 수
 * 있다(시세 조회에 실패한 종목은 서버가 알아서 제외).
 *
 * <p>{@code changeRate}는 소수 비율(예: {@code "0.0125"} = 1.25%)로
 * 내려온다 - 이 프로젝트의 기존 자체 계산 등락률(퍼센트 숫자, 예:
 * {@code 1.25})과 단위가 다르므로 호출 측에서 반드시 ×100 해야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossRankingResponse(
    RankedResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankedResult(
        String rankedAt,
        List<RankingItem> rankings
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankingItem(
        int rank,
        String symbol,
        String currency,
        RankingPrice price,
        String tradingVolume,
        String tradingAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankingPrice(
        String lastPrice,
        String basePrice,
        String changeRate
    ) {
    }
}
