package com.quantlime.stock.dto.mapper;

import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.response.StockDetailResponse;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class StockMapper {

    // 네이버 금융 API(api.stock.naver.com/stock/{reutersCode}/basic) 응답의
    // itemLogoPngUrl 필드에서 확인한 정적 경로 패턴. 별도 API 호출 없이
    // reutersCode만으로 결정적으로 생성 가능하나, 네이버가 공식 문서화한
    // 계약은 아니라 종목별 로고 부재/경로 변경 가능성은 프론트 onError
    // 폴백으로 방어한다.
    private static final String LOGO_URL_FORMAT =
        "https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/Stock%s.png";

    // 국내는 reutersCode == stockCode 그대로지만, 해외는 거래소별 접미사가
    // 붙는다 - 나스닥은 ".O"(예: AAPL → AAPL.O), 뉴욕증권거래소는 접미사
    // 없음(예: JPM 그대로). 라이브 호출(api.stock.naver.com/stock/{code}/basic)로
    // 실제 확인한 규칙(2026-07-31) - AMEX 등 다른 해외 거래소는 이 프로젝트가
    // 아직 다루지 않아(MarketType 참고) 매핑 대상에서 제외.
    private static final String NASDAQ_REUTERS_SUFFIX = ".O";

    public static StockDetailResponse toStockDetailResponse(Stock stock) {
        return new StockDetailResponse(
            stock.getId(),
            stock.getStockCode(),
            stock.getDisplayName(),
            stock.getMarketType().getLabel(),
            stock.getListingStatus().getLabel(),
            stock.getSector(),
            toLogoUrl(stock)
        );
    }

    // 랭킹/관심종목 응답(MarketRankingResponse, WatchlistResponse)도 같은
    // 규칙으로 로고 URL을 내려주기 위해 공개 - 이전엔 프론트가 stockCode만
    // 가지고 이 패턴을 직접 조립했는데(frontend/src/utils/stockLogo.ts),
    // 나스닥 접미사를 붙이려면 시장 구분을 알아야 해서 시장 구분을 이미
    // 알고 있는 백엔드 쪽으로 계산 위치를 옮겼다(2026-07-31).
    public static String toLogoUrl(Stock stock) {
        return LOGO_URL_FORMAT.formatted(toReutersCode(stock));
    }

    private static String toReutersCode(Stock stock) {
        if (stock.getMarketType() == MarketType.NASDAQ) {
            return stock.getStockCode() + NASDAQ_REUTERS_SUFFIX;
        }
        return stock.getStockCode();
    }
}
