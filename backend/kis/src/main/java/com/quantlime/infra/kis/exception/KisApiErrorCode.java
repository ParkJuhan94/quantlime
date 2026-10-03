package com.quantlime.infra.kis.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KisApiErrorCode implements ErrorCode {

    MASTER_FILE_DOWNLOAD_FAILED("KIS 해외주식 종목정보 마스터파일 다운로드에 실패했습니다.", "KIS_004");

    private final String message;
    private final String code;
}
