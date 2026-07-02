package com.quantlab.common.controller;

import com.quantlab.price.scheduler.OhlcvCollectorScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "개발용 API")
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev")
public class DevController {

    private final OhlcvCollectorScheduler ohlcvCollectorScheduler;

    @PostMapping("/ohlcv/collect")
    @Operation(summary = "[개발용] OHLCV 수집 수동 트리거")
    public ResponseEntity<String> triggerOhlcvCollect() {
        ohlcvCollectorScheduler.collectDailyOhlcv();
        return ResponseEntity.ok("OHLCV 수집 완료");
    }
}
