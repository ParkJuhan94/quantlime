package com.quantlime.price.controller;

import com.quantlime.price.dto.response.CurrentPriceResponse;
import com.quantlime.price.dto.response.DailyChartResponse;
import com.quantlime.price.service.StockPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "시세 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class PriceController {

    private final StockPriceService stockPriceService;

    @GetMapping("/{stockCode}/price")
    @Operation(
        summary = "현재가 조회",
        description = "종목의 현재가를 조회한다. 시세가 없으면 price=null로 응답한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<CurrentPriceResponse> getCurrentPrice(
        @PathVariable String stockCode) {
        return ResponseEntity.ok(stockPriceService.getCurrentPrice(stockCode));
    }

    @GetMapping("/{stockCode}/chart")
    @Operation(
        summary = "일봉 차트 조회",
        description = "종목의 최근 N일 일봉 OHLCV를 조회한다"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<DailyChartResponse>> getChart(
        @PathVariable String stockCode,
        @RequestParam(defaultValue = "daily")
        @Pattern(regexp = "daily", message = "period는 daily만 지원합니다.") String period,
        @RequestParam(defaultValue = "90")
        @Min(value = 1, message = "days는 1 이상이어야 합니다.")
        @Max(value = 365, message = "days는 365 이하여야 합니다.") int days) {
        return ResponseEntity.ok(stockPriceService.getChart(stockCode, days));
    }
}
