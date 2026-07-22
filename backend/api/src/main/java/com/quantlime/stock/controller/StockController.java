package com.quantlime.stock.controller;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.stock.cache.PopularStocksCache;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.mapper.StockMapper;
import com.quantlime.stock.dto.response.StockDetailResponse;
import com.quantlime.stock.dto.response.StockFundamentalsResponse;
import com.quantlime.stock.service.StockFundamentalsService;
import com.quantlime.stock.service.StockMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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
    private final StockFundamentalsService stockFundamentalsService;
    private final PopularStocksCache popularStocksCache;

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

    @GetMapping("/{stockCode}/fundamentals")
    @Operation(
        summary = "종목 밸류에이션 지표 조회",
        description = "시가총액/PER/포워드PER/PBR/PSR/부채비율을 조회한다(값이 없으면 null)"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<StockFundamentalsResponse> getFundamentals(
        @PathVariable String stockCode) {
        return ResponseEntity.ok(stockFundamentalsService.getFundamentals(stockCode));
    }

    @GetMapping("/popular")
    @Operation(
        summary = "인기 종목 조회",
        description = "관심종목으로 등록한 사용자 수가 많은 순으로 상위 N개 종목을 조회한다(검색모달 '인기 종목')"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<StockDetailResponse>> getPopularStocks(
        @RequestParam(defaultValue = "5")
        @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
        @Max(value = 20, message = "limit는 20 이하여야 합니다.") int limit) {
        return ResponseEntity.ok(popularStocksCache.get(limit));
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
