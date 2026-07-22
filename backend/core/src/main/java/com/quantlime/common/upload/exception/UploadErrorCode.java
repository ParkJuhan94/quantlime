package com.quantlime.common.upload.exception;

import com.quantlime.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UploadErrorCode implements ErrorCode {

    EMPTY_FILE("빈 파일은 업로드할 수 없습니다.", "UPLOAD_000"),
    INVALID_FILE_TYPE("이미지 파일(jpg, png, gif, webp)만 업로드할 수 있습니다.", "UPLOAD_001"),
    FILE_TOO_LARGE("파일 크기는 5MB를 넘을 수 없습니다.", "UPLOAD_002");

    private final String message;
    private final String code;
}
