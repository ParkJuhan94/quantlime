package com.quantlime.stock.implement;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 종목 마스터 조회를 감싸는 구현 레이어(Implementation) - 다른 도메인의 캐시·스케줄러가
 * stock의 Repository를 직접 잡지 않고 이 컴포넌트를 통해서만 읽게 한다. 메서드 이름은
 * Repository와 같게 두어 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class StockReader {

    private final StockRepository stockRepository;

    public List<Stock> findByListingStatusAndMarketTypeInAndPriceUnsupportedFalse(
        ListingStatus listingStatus, List<MarketType> marketTypes) {
        return stockRepository.findByListingStatusAndMarketTypeInAndPriceUnsupportedFalse(
            listingStatus, marketTypes);
    }

    public List<Stock> findByStockCodeIn(List<String> stockCodes) {
        return stockRepository.findByStockCodeIn(stockCodes);
    }
}
