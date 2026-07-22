package com.quantlime.common.util;

import com.quantlime.common.exception.ErrorCode;
import com.quantlime.common.exception.ExternalApiException;
import java.util.function.Supplier;

/**
 * "호출 → 응답 null 체크 → 특정 실패코드로 예외 → 그 외 예외는 일반화해서
 * 래핑"이라는 형태를 여러 외부 API 클라이언트(Toss, 퀀트 엔진)가 각자
 * 재구현하고 있어 공용 유틸로 추출했다.
 */
public final class ExternalApiInvoker {

    private ExternalApiInvoker() {
    }

    public static <T> T call(ErrorCode fallbackErrorCode, Supplier<T> supplier) {
        try {
            T result = supplier.get();
            if (result == null) {
                throw new ExternalApiException(fallbackErrorCode);
            }
            return result;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(fallbackErrorCode, e);
        }
    }

    /**
     * 특정 예외 타입(예: Rate Limit)만 별도의 ErrorCode로 매핑하고 싶을 때
     * 사용한다. 그 외 예외는 fallbackErrorCode로 일반화해서 래핑한다.
     */
    public static <T> T call(
        ErrorCode fallbackErrorCode, Supplier<T> supplier,
        Class<? extends Exception> specialException, ErrorCode specialErrorCode) {
        try {
            T result = supplier.get();
            if (result == null) {
                throw new ExternalApiException(fallbackErrorCode);
            }
            return result;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            if (specialException.isInstance(e)) {
                throw new ExternalApiException(specialErrorCode, e);
            }
            throw new ExternalApiException(fallbackErrorCode, e);
        }
    }
}
