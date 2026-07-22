package com.quantlime.infra.kis.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KisApiErrorCode implements ErrorCode {

    TOKEN_ISSUANCE_FAILED("KIS API 토큰 발급에 실패했습니다.", "KIS_000"),
    OVERSEAS_PRICE_INQUIRY_FAILED("KIS API 해외주식 현재가 조회에 실패했습니다.", "KIS_001"),
    OVERSEAS_DAILY_PRICE_INQUIRY_FAILED("KIS API 해외주식 기간별시세 조회에 실패했습니다.", "KIS_002"),
    RATE_LIMIT_EXCEEDED("KIS API 요청 한도를 초과했습니다.", "KIS_003"),
    MASTER_FILE_DOWNLOAD_FAILED("KIS 해외주식 종목정보 마스터파일 다운로드에 실패했습니다.", "KIS_004");

    private final String message;
    private final String code;
}
