package com.quantlime.infra.toss.dto;

import java.util.List;

public record TossStockWarningResponse(
    List<TossStockWarning> result
) {

    // warningType은 스펙상 unknown code를 허용해야 하므로 enum이 아니라 문자열로 받는다.
    public record TossStockWarning(
        String warningType,
        String exchange,
        String startDate,
        String endDate
    ) {
    }
}
