package com.quantlime.batch.common;

/**
 * 배치를 시작할 수 없는 상태(이미 실행 중, 메타 테이블 누락, 재시작 불가 상태)일 때 던진다. 삭제 단계가 있는 Job은 거부 시점이 실행 전이어야
 * 하므로 모든 거부는 Job을 시작하기 전에 일어난다.
 */
public class BatchLaunchRejectedException extends RuntimeException {

    public BatchLaunchRejectedException(String message) {
        super(message);
    }

    public BatchLaunchRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
