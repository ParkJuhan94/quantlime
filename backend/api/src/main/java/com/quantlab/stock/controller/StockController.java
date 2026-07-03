package com.quantlab.stock.controller;

import com.quantlab.common.dto.PageResponse;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.dto.mapper.StockMapper;
import com.quantlab.stock.dto.response.StockDetailResponse;
import com.quantlab.stock.service.StockMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "종목 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class StockController {

    private final StockMasterService stockMasterService;

    @GetMapping("/{stockCode}")
    @Operation(
        summary = "종목 상세 조회",
        description = "종목 코드로 종목 정보를 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<StockDetailResponse> getStock(
        @PathVariable String stockCode) {
        Stock stock = stockMasterService.getStockByCode(stockCode);
        return ResponseEntity.ok(
            StockMapper.toStockDetailResponse(stock));
    }

    @GetMapping("/search")
    @Operation(
        summary = "종목 검색",
        description = "종목명 또는 종목 코드로 종목을 검색한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<PageResponse<StockDetailResponse>> searchStocks(
        @RequestParam @NotBlank(message = "검색어는 필수입니다.") String q,
        Pageable pageable) {
        Slice<Stock> stocks = stockMasterService.searchStocks(q, pageable);
        return ResponseEntity.ok(
            PageResponse.of(stocks.map(StockMapper::toStockDetailResponse)));
    }
}
