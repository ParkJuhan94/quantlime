package com.quantlime.common.util;

/**
 * "잠깐 대기 후 계속 진행, 인터럽트되면 스레드 인터럽트 상태를 보존하고
 * 중단 신호를 알린다"는 패턴(백필 페이지네이션 간 딜레이, 레이트리밋
 * 백오프 등)을 여러 클래스가 각자 try/catch(InterruptedException)로
 * 재구현하고 있어 공용 유틸로 추출했다(2026-08-01,
 * DomesticDailyPriceService/OverseasDailyPriceBackfillService의 동일한 10줄 복붙
 * 제거가 계기).
 */
public final class SleepUtil {

    private SleepUtil() {
    }

    /**
     * @return 정상적으로 대기를 마쳤으면 true, 인터럽트로 중단됐으면 false
     * (이 경우 호출 측이 재시도/후속 작업 없이 즉시 중단해야 한다 - 스레드
     * 인터럽트 상태는 이미 복원해뒀다).
     */
    public static boolean sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
