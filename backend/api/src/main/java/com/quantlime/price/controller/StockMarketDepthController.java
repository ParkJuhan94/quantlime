package com.quantlime.price.controller;

import com.quantlime.price.dto.response.OrderbookResponse;
import com.quantlime.price.dto.response.PriceLimitResponse;
import com.quantlime.price.dto.response.StockWarningResponse;
import com.quantlime.price.dto.response.TradeResponse;
import com.quantlime.price.service.StockMarketDepthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "호가·체결 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/{stockCode}")
public class StockMarketDepthController {

    private final StockMarketDepthService stockMarketDepthService;

    @GetMapping("/orderbook")
    @Operation(summary = "호가 조회", description = "매도/매수 호가와 잔량을 조회한다(서버가 2초 캐싱, 로그인 불필요)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<OrderbookResponse> getOrderbook(@PathVariable String stockCode) {
        return ResponseEntity.ok(stockMarketDepthService.getOrderbook(stockCode));
    }

    @GetMapping("/trades")
    @Operation(summary = "최근 체결 내역 조회", description = "최근 체결 30건을 최신순으로 조회한다(서버가 2초 캐싱)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<TradeResponse>> getTrades(@PathVariable String stockCode) {
        return ResponseEntity.ok(stockMarketDepthService.getTrades(stockCode));
    }

    @GetMapping("/price-limits")
    @Operation(summary = "상/하한가 조회", description = "가격제한이 없는 시장(미국 등)은 상/하한가가 null이다")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PriceLimitResponse> getPriceLimit(@PathVariable String stockCode) {
        return ResponseEntity.ok(stockMarketDepthService.getPriceLimit(stockCode));
    }

    @GetMapping("/warnings")
    @Operation(summary = "매수 유의사항 조회", description = "투자경고/단기과열/VI 발동 등 현재 적용 중인 유의사항 목록(없으면 빈 배열)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<StockWarningResponse>> getWarnings(@PathVariable String stockCode) {
        return ResponseEntity.ok(stockMarketDepthService.getWarnings(stockCode));
    }
}
