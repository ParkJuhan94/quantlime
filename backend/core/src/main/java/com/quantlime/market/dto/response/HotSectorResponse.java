package com.quantlime.market.dto.response;

import java.util.List;

/**
 * 섹터(산업)별 거래대금 가중 평균 등락률. leaders는 그 섹터의 대표 종목(가중치가 큰 순).
 */
public record HotSectorResponse(
    String sector,
    double changeRate,
    int stockCount,
    List<Leader> leaders
) {

    public record Leader(String stockCode, String stockName, double changeRate) {
    }
}
