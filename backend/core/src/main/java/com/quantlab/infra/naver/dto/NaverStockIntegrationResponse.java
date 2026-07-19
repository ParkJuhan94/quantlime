package com.quantlab.infra.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GET /api/stock/{code}/integration 응답 - 시총/PER/PBR 등은 totalInfos
 * 배열에 code(기계가 읽는 식별자)/key(한글 표시명)/value 형태로 섞여
 * 내려온다. code로 매칭한다(key는 표시용 한글이라 바뀔 여지가 더 큼).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverStockIntegrationResponse(
    List<TotalInfo> totalInfos
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TotalInfo(
        String code,
        String key,
        String value
    ) {
    }
}
