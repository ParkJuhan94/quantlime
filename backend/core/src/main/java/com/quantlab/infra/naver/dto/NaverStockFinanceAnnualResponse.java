package com.quantlab.infra.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * GET /api/stock/{code}/finance/annual 응답 - 연도별 재무 항목이 행(매출액,
 * 부채비율 등)×열(연도) 표 형태로 내려온다. trTitleList가 열(연도) 목록이고
 * isConsensus="Y"면 아직 확정 안 된 컨센서스(추정치) 연도라 실제값에서
 * 제외해야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverStockFinanceAnnualResponse(
    FinanceInfo financeInfo
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinanceInfo(
        List<TrTitle> trTitleList,
        List<Row> rowList
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrTitle(
        String key,
        String title,
        String isConsensus
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
        String title,
        Map<String, ColumnValue> columns
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ColumnValue(
        String value
    ) {
    }
}
