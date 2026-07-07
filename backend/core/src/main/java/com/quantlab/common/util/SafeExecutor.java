package com.quantlab.common.util;

import lombok.extern.slf4j.Slf4j;

/**
 * "실패해도 로그만 남기고 흐름은 계속 진행" 패턴(백필/스코어 재계산 트리거
 * 등, 실패가 상위 흐름을 막아서는 안 되는 부수 작업)을 한 곳에 모은 유틸.
 */
@Slf4j
public final class SafeExecutor {

    private SafeExecutor() {
    }

    public static void runSafely(String context, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("{} 실패: error={}", context, e.getMessage(), e);
        }
    }
}
