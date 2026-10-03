package com.quantlime.infra.naver.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NaverFinanceApiErrorCode implements ErrorCode {

    INDEX_INQUIRY_FAILED("지수 시세 조회에 실패했습니다.", "NAVER_FIN_000"),
    INDEX_CHART_INQUIRY_FAILED("지수 차트 조회에 실패했습니다.", "NAVER_FIN_001"),
    INDEX_MINUTE_CHART_INQUIRY_FAILED("지수 분봉 차트 조회에 실패했습니다.", "NAVER_FIN_002"),
    STOCK_FUNDAMENTALS_INQUIRY_FAILED("종목 밸류에이션 지표 조회에 실패했습니다.", "NAVER_FIN_003");

    private final String message;
    private final String code;
}
