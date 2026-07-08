package com.quantlab.stock.scheduler;

import com.quantlab.common.util.SafeExecutor;
import com.quantlab.stock.service.StockMasterSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockMasterSyncScheduler {

    private final StockMasterSyncService stockMasterSyncService;

    // 신규상장/상장폐지는 일 단위로 쫓을 만큼 자주 발생하지 않는다 - 주 1회,
    // 장이 열리지 않는 일요일 새벽으로 충분하다.
    @Scheduled(cron = "0 0 3 * * SUN", zone = "Asia/Seoul")
    public void syncStockMaster() {
        SafeExecutor.runSafely("종목마스터 동기화", stockMasterSyncService::syncStockMaster);
    }
}
