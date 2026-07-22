package com.quantlime.payment.controller;

import com.quantlime.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 헤더명(TossPayments-Webhook-Signature)은 실제 웹훅을 받아보며(또는
// 토스페이먼츠 웹훅 문서로) 확정 필요 - TossWebhookVerifier 클래스 주석 참고.
@Tag(name = "결제 웹훅 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/webhooks")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/tosspayments")
    @Operation(summary = "토스페이먼츠 웹훅 수신")
    @ApiResponse(useReturnTypeSchema = true)
    public ResponseEntity<Void> handleWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "TossPayments-Webhook-Signature", required = false) String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
