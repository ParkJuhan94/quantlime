package com.quantlime.infra.toss.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TossApiErrorCode implements ErrorCode {

    TOKEN_ISSUANCE_FAILED("토스증권 API 토큰 발급에 실패했습니다.", "TOSS_000"),
    PRICE_INQUIRY_FAILED("토스증권 API 시세 조회에 실패했습니다.", "TOSS_001"),
    CANDLE_INQUIRY_FAILED("토스증권 API 캔들 조회에 실패했습니다.", "TOSS_002"),
    STOCK_INFO_INQUIRY_FAILED("토스증권 API 종목 정보 조회에 실패했습니다.", "TOSS_003"),
    RATE_LIMIT_EXCEEDED("토스증권 API 요청 한도를 초과했습니다.", "TOSS_004"),
    INVALID_RESPONSE("토스증권 API 응답이 유효하지 않습니다.", "TOSS_005"),
    MARKET_CALENDAR_INQUIRY_FAILED("토스증권 API 장 운영 캘린더 조회에 실패했습니다.", "TOSS_006"),
    EXCHANGE_RATE_INQUIRY_FAILED("토스증권 API 환율 조회에 실패했습니다.", "TOSS_007"),
    RANKING_INQUIRY_FAILED("토스증권 API 랭킹 조회에 실패했습니다.", "TOSS_008"),
    US_MARKET_CALENDAR_INQUIRY_FAILED("토스증권 API 해외 장 운영 캘린더 조회에 실패했습니다.", "TOSS_009"),
    MARKET_INDICATOR_PRICE_INQUIRY_FAILED("토스증권 API 시장 지표 현재가 조회에 실패했습니다.", "TOSS_010"),
    MARKET_INDICATOR_CANDLE_INQUIRY_FAILED("토스증권 API 시장 지표 캔들 조회에 실패했습니다.", "TOSS_011"),
    INVESTOR_TRADING_INQUIRY_FAILED("토스증권 API 투자자별 매매대금 조회에 실패했습니다.", "TOSS_012");

    private final String message;
    private final String code;
}
