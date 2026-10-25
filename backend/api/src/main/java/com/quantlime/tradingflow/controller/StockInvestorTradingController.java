package com.quantlime.tradingflow.controller;

import com.quantlime.tradingflow.dto.response.StockInvestorTradingResponse;
import com.quantlime.tradingflow.service.StockInvestorTradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@Tag(name = "종목별 투자자 매매동향 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/{stockCode}")
public class StockInvestorTradingController {

    private final StockInvestorTradingService stockInvestorTradingService;

    @GetMapping("/investor-trading")
    @Operation(
        summary = "종목별 투자자 매매동향 조회",
        description = "국내 종목의 최근 N거래일 개인/외국인/기관/기타법인 순매수 거래량(주)을 오름차순으로 조회한다. "
            + "당일은 장중 잠정치라 개인·기타법인이 null이고 저녁에 확정된다(confirmed). 해외 종목은 빈 배열"
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<StockInvestorTradingResponse>> getInvestorTrading(
        @PathVariable String stockCode,
        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "count는 1 이상이어야 합니다.")
        @Max(value = 250, message = "count는 250 이하여야 합니다.") int count) {
        return ResponseEntity.ok(stockInvestorTradingService.getRecent(stockCode, count));
    }
}
