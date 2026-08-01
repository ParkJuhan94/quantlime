package com.quantlime.backtest.controller;

import com.quantlime.auth.resolver.OptionalLoginUser;
import com.quantlime.backtest.dto.response.BacktestResponse;
import com.quantlime.backtest.service.BacktestService;
import com.quantlime.common.exception.ForbiddenException;
import com.quantlime.subscription.exception.SubscriptionErrorCode;
import com.quantlime.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "백테스트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BacktestController {

    private final BacktestService backtestService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/backtest/{stockCode}")
    @Operation(
        summary = "종목 백테스트 결과 조회(구독자 전용)",
        description = "가장 최근에 계산된 스코어 버전 기준으로, 추세추종/평균회귀 두 축 x "
            + "horizon(5/10/20/60일)별 Rank IC·분위수 버킷·스코어 안정성을 조회한다. "
            + "구독중(status=ACTIVE)이 아니면 403(SUB_006)."
    )
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<BacktestResponse> getBacktestResult(
        @PathVariable String stockCode, @OptionalLoginUser Long userId) {
        if (!subscriptionService.hasActivePremium(userId)) {
            throw new ForbiddenException(SubscriptionErrorCode.PREMIUM_REQUIRED);
        }
        return ResponseEntity.ok(backtestService.getBacktestResult(stockCode));
    }
}
