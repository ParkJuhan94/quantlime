package com.quantlime.score.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public record ScoreRankingResponse(
    String stockCode,
    String stockName,
    String sector,
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul") LocalDate scoreDate,
    Double trendScore,
    Double meanReversionScore,
    Double compositeScore,
    // v3.0부터 도입 - 같은 날짜·같은 모집단(국내/해외) 대비 상대 순위
    // (0~100, 높을수록 상위). 랭킹 정렬·화면 표시의 기준값은 이 값이고,
    // compositeScore(원점수)는 참고용으로 함께 내려준다 - 절대 점수만으로는
    // 분포가 40~60에 쏠려 변별력이 없었다(2026-09 감사 세션).
    Double trendPercentile,
    Double meanReversionPercentile,
    Double compositePercentile,
    String grade,
    boolean insufficientData,
    String logoUrl,
    boolean overseas,
    // 최근 20거래일 일평균 거래대금(원/달러, StockLiquidity 참고) - 스코어
    // 모드 랭킹에서 항상 "-"였던 거래대금 컬럼을 채운다(2026-09 감사
    // 세션 - 유동성 필터를 넣는 이상 사용자가 그 값을 봐야 왜 이 종목만
    // 보이는지 납득된다). 유동성 스냅샷이 아직 없는 종목은 null.
    Double avgTradingValue
) {
}
