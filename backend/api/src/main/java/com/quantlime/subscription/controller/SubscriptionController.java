package com.quantlime.subscription.controller;

import com.quantlime.auth.resolver.LoginUser;
import com.quantlime.payment.dto.mapper.PaymentMapper;
import com.quantlime.payment.dto.response.PaymentResponse;
import com.quantlime.payment.service.PaymentService;
import com.quantlime.subscription.domain.Subscription;
import com.quantlime.subscription.dto.mapper.SubscriptionMapper;
import com.quantlime.subscription.dto.request.IssueBillingKeyRequest;
import com.quantlime.subscription.dto.response.SubscriptionMeResponse;
import com.quantlime.subscription.dto.response.SubscriptionPlanResponse;
import com.quantlime.subscription.dto.response.SubscriptionResponse;
import com.quantlime.subscription.service.SubscriptionPlanService;
import com.quantlime.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "구독 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final SubscriptionPlanService subscriptionPlanService;
    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;

    @GetMapping("/plans")
    @Operation(summary = "판매중인 구독 플랜 목록 조회")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<SubscriptionPlanResponse>> getPlans() {
        List<SubscriptionPlanResponse> response = subscriptionPlanService.getActivePlans().stream()
            .map(SubscriptionMapper::toSubscriptionPlanResponse)
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "내 구독 상태 조회(customerKey 포함)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<SubscriptionMeResponse> getMySubscription(@LoginUser Long userId) {
        SubscriptionResponse subscriptionResponse = subscriptionService.findByUserId(userId)
            .map(SubscriptionMapper::toSubscriptionResponse)
            .orElse(null);
        String customerKey = paymentService.toCustomerKey(userId);
        return ResponseEntity.ok(new SubscriptionMeResponse(customerKey, subscriptionResponse));
    }

    @PostMapping("/billing-key")
    @Operation(summary = "카드 등록(빌링키 발급) + 즉시 첫 결제")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<SubscriptionResponse> issueBillingKey(
        @LoginUser Long userId, @Valid @RequestBody IssueBillingKeyRequest request) {
        Subscription subscription = paymentService.issueBillingKeyAndSubscribe(
            userId, request.authKey(), request.planCode(), request.installmentMonths());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(SubscriptionMapper.toSubscriptionResponse(subscription));
    }

    @PostMapping("/cancel")
    @Operation(summary = "구독 자동갱신 해지(현재 주기 종료까지는 계속 이용 가능)")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> cancelAutoRenew(@LoginUser Long userId) {
        subscriptionService.cancelAutoRenew(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payments")
    @Operation(summary = "결제 이력 조회")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<List<PaymentResponse>> getPaymentHistory(@LoginUser Long userId) {
        List<PaymentResponse> response = paymentService.getPaymentHistory(userId).stream()
            .map(PaymentMapper::toPaymentResponse)
            .toList();
        return ResponseEntity.ok(response);
    }
}
