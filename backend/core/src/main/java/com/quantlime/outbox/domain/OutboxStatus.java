package com.quantlime.outbox.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxStatus {

    PENDING("발행 대기"),
    PUBLISHED("발행 완료");

    private final String label;
}
