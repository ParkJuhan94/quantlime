package com.quantlime.stock.service;

import com.quantlime.infra.kind.KindApiClient;
import com.quantlime.infra.kind.dto.KindStockInfo;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.StockMasterSyncResult;
import com.quantlime.stock.repository.StockRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMasterSyncService {

    private final KindApiClient kindApiClient;
    private final StockRepository stockRepository;

    @Transactional
    public StockMasterSyncResult syncStockMaster() {
        Map<String, KindStockInfo> latest = fetchLatestCorpList();
        Map<String, Stock> existingByCode = stockRepository.findAll().stream()
            .collect(Collectors.toMap(Stock::getStockCode, Function.identity()));

        int newlyListedCount = registerNewlyListed(latest, existingByCode);
        int delistedCount = markDelisted(latest, existingByCode);

        log.info("종목마스터 동기화 완료: 신규상장={}, 상장폐지={}",
            newlyListedCount, delistedCount);
        return new StockMasterSyncResult(newlyListedCount, delistedCount);
    }

    private Map<String, KindStockInfo> fetchLatestCorpList() {
        Map<String, KindStockInfo> result = new LinkedHashMap<>();
        // KIND는 국내 거래소 전용 소스라 해외 시장(NASDAQ/NYSE)은 제외한다 -
        // MarketType.values() 그대로 순회하면 KindApiClient가 예외를 던진다.
        for (MarketType marketType : MarketType.domesticValues()) {
            List<KindStockInfo> stocks = kindApiClient.fetchCorpList(marketType);
            for (KindStockInfo stock : stocks) {
                // 동일 코드가 KIND 응답 내에서 중복 행으로 내려오는 경우가
                // 있어 먼저 잡힌 값을 그대로 유지한다
                result.putIfAbsent(stock.stockCode(), stock);
            }
        }
        return result;
    }

    private int registerNewlyListed(
        Map<String, KindStockInfo> latest, Map<String, Stock> existingByCode) {
        int count = 0;
        for (KindStockInfo info : latest.values()) {
            if (existingByCode.containsKey(info.stockCode())) {
                continue;
            }
            stockRepository.save(Stock.of(
                info.stockCode(), info.stockName(), info.marketType(),
                ListingStatus.LISTED, info.sector()));
            count++;
        }
        return count;
    }

    private int markDelisted(
        Map<String, KindStockInfo> latest, Map<String, Stock> existingByCode) {
        int count = 0;
        for (Stock stock : existingByCode.values()) {
            boolean stillListed = latest.containsKey(stock.getStockCode());
            if (stock.getListingStatus() == ListingStatus.LISTED && !stillListed) {
                stock.updateListingStatus(ListingStatus.DELISTED);
                count++;
            }
        }
        return count;
    }
}
