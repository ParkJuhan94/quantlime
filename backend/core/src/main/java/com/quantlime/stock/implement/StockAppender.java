package com.quantlime.stock.implement;

import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 종목 마스터 저장을 감싸는 구현 레이어 - 서비스가 stock의 Repository를 직접 잡지 않고
 * 이 컴포넌트를 통해서만 쓰게 한다. 트랜잭션 경계는 호출하는 서비스가 소유한다.
 */
@Component
@RequiredArgsConstructor
public class StockAppender {

    private final StockRepository stockRepository;

    public Stock save(Stock stock) {
        return stockRepository.save(stock);
    }

    public void saveAll(List<Stock> stocks) {
        stockRepository.saveAll(stocks);
    }
}
