package com.quantlime.price.service;

import com.quantlime.infra.toss.dto.TossOrderbookResponse;
import com.quantlime.infra.toss.dto.TossPriceLimitResponse;
import com.quantlime.infra.toss.dto.TossStockWarningResponse;
import com.quantlime.infra.toss.dto.TossTradeResponse;
import com.quantlime.price.cache.StockMarketDepthCache;
import com.quantlime.price.dto.response.OrderbookResponse;
import com.quantlime.price.dto.response.PriceLimitResponse;
import com.quantlime.price.dto.response.StockWarningResponse;
import com.quantlime.price.dto.response.TradeResponse;
import com.quantlime.stock.service.StockMasterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 종목 상세의 호가/체결/상하한가/매수유의. 종목 존재 여부만 로컬 마스터로
 * 검증하고(없으면 404) 나머지는 토스 응답을 {@link StockMarketDepthCache}로
 * 짧게 캐싱해 그대로 변환한다. 토스 심볼은 종목코드와 동일하다(랭킹 캐시와 같은 전제).
 */
@Service
@RequiredArgsConstructor
public class StockMarketDepthService {

    private final StockMasterService stockMasterService;
    private final StockMarketDepthCache stockMarketDepthCache;

    public OrderbookResponse getOrderbook(String stockCode) {
        TossOrderbookResponse.Orderbook orderbook = stockMarketDepthCache.orderbook(symbolOf(stockCode));
        return new OrderbookResponse(
            orderbook.timestamp(), orderbook.currency(),
            toLevels(orderbook.asks()), toLevels(orderbook.bids()));
    }

    public List<TradeResponse> getTrades(String stockCode) {
        TossTradeResponse response = stockMarketDepthCache.trades(symbolOf(stockCode));
        if (response == null || response.result() == null) {
            return List.of();
        }
        return response.result().stream()
            .map(trade -> new TradeResponse(
                parse(trade.price()), parse(trade.volume()), trade.timestamp(), trade.currency()))
            .toList();
    }

    public PriceLimitResponse getPriceLimit(String stockCode) {
        TossPriceLimitResponse.PriceLimit limit = stockMarketDepthCache.priceLimit(symbolOf(stockCode));
        return new PriceLimitResponse(parse(limit.upperLimitPrice()), parse(limit.lowerLimitPrice()), limit.currency());
    }

    public List<StockWarningResponse> getWarnings(String stockCode) {
        TossStockWarningResponse response = stockMarketDepthCache.warnings(symbolOf(stockCode));
        if (response == null || response.result() == null) {
            return List.of();
        }
        return response.result().stream()
            .map(warning -> new StockWarningResponse(
                warning.warningType(), labelOf(warning.warningType()),
                warning.exchange(), warning.startDate(), warning.endDate()))
            .toList();
    }

    private String symbolOf(String stockCode) {
        return stockMasterService.getStockByCode(stockCode).getStockCode();
    }

    private List<OrderbookResponse.Level> toLevels(List<TossOrderbookResponse.OrderbookEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
            .map(entry -> new OrderbookResponse.Level(parse(entry.price()), parse(entry.volume())))
            .toList();
    }

    private static Double parse(String decimal) {
        return decimal == null ? null : Double.valueOf(decimal);
    }

    // 스펙상 unknown code를 허용해야 하므로(StockWarning.warningType) 모르는 값은 원문을 그대로 노출한다.
    private static String labelOf(String warningType) {
        if (warningType == null) {
            return "";
        }
        return switch (warningType) {
            case "LIQUIDATION_TRADING" -> "정리매매";
            case "OVERHEATED" -> "단기과열종목";
            case "INVESTMENT_WARNING" -> "투자경고종목";
            case "INVESTMENT_RISK" -> "투자위험종목";
            case "VI_STATIC_AND_DYNAMIC" -> "VI 발동(정적+동적)";
            case "VI_STATIC" -> "VI 발동(정적)";
            case "VI_DYNAMIC" -> "VI 발동(동적)";
            case "STOCK_WARRANTS" -> "신주인수권";
            default -> warningType;
        };
    }
}
