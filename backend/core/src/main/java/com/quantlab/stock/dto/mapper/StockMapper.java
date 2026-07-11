package com.quantlab.stock.dto.mapper;

import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.dto.response.StockDetailResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class StockMapper {

    // 네이버 금융 모바일 API(m.stock.naver.com/api/stock/{code}/basic) 응답의
    // itemLogoPngUrl 필드에서 확인한 정적 경로 패턴. 별도 API 호출 없이
    // 종목 코드만으로 결정적으로 생성 가능하나, 네이버가 공식 문서화한
    // 계약은 아니라 종목별 로고 부재/경로 변경 가능성은 프론트 onError
    // 폴백으로 방어한다.
    private static final String LOGO_URL_FORMAT =
        "https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/Stock%s.png";

    public static StockDetailResponse toStockDetailResponse(Stock stock) {
        return new StockDetailResponse(
            stock.getId(),
            stock.getStockCode(),
            stock.getStockName(),
            stock.getMarketType().getLabel(),
            stock.getListingStatus().getLabel(),
            stock.getSector(),
            toLogoUrl(stock.getStockCode())
        );
    }

    private static String toLogoUrl(String stockCode) {
        return LOGO_URL_FORMAT.formatted(stockCode);
    }
}
