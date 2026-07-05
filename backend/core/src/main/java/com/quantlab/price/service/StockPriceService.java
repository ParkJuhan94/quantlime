package com.quantlab.price.service;

import com.quantlab.infra.toss.TossApiClient;
import com.quantlab.infra.toss.dto.TossPriceResponse;
import com.quantlab.price.dto.mapper.PriceMapper;
import com.quantlab.price.dto.response.CurrentPriceResponse;
import com.quantlab.price.dto.response.DailyChartResponse;
import com.quantlab.stock.service.StockMasterService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final StockMasterService stockMasterService;
    private final TossApiClient tossApiClient;
    private final DailyPriceService dailyPriceService;

    @Transactional(readOnly = true)
    public CurrentPriceResponse getCurrentPrice(String stockCode) {
        stockMasterService.getStockByCode(stockCode);

        TossPriceResponse response = tossApiClient.getCurrentPrices(stockCode);
        List<TossPriceResponse.TossPrice> prices = response.result();
        if (prices == null || prices.isEmpty()) {
            log.warn("현재가 없음: stockCode={}", stockCode);
            return new CurrentPriceResponse(stockCode, null, null, null);
        }

        return PriceMapper.toCurrentPriceResponse(stockCode, prices.get(0));
    }

    @Transactional(readOnly = true)
    public List<DailyChartResponse> getChart(String stockCode, int days) {
        stockMasterService.getStockByCode(stockCode);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        return dailyPriceService.getDailyPrices(stockCode, start, end).stream()
            .map(PriceMapper::toDailyChartResponse)
            .toList();
    }
}
