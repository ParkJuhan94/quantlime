package com.quantlime.outbox.dto;

/** 아직 브로커로 못 보낸 아웃박스 행 수와 그중 가장 오래된 행의 나이(초). 없으면 둘 다 0. */
public record OutboxBacklog(long pendingCount, long oldestPendingAgeSeconds) {
}
