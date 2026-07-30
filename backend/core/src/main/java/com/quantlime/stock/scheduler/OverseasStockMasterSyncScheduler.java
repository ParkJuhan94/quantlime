package com.quantlime.stock.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.stock.service.OverseasStockMasterSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 국내(StockMasterSyncScheduler)와 달리 지금까지 수동 트리거(/dev/stock-master/
 * overseas/sync)만 있고 자동 스케줄러가 없었다(2026-07-30 추가) - 그래서
 * KIS 마스터파일에 섞인 Toss 미지원 형식 종목("AAC/UN" 등, 실제로는
 * OverseasStockMasterSyncService.syncMarket이 매번 재검증해 price_unsupported로
 * 표시하는 로직도 이 스케줄러가 있어야 정기적으로 돈다)이 한번 등록되면
 * 사용자가 수동으로 /dev 엔드포인트를 부르기 전까진 계속 남아있었다.
 * 신규상장/상장폐지도 자주 발생하지 않아 국내와 동일하게 주 1회면 충분 -
 * 도메스틱 동기화(일요일 03:00)와 겹치지 않게 30분 오프셋을 둔다.
 */
@Component
@RequiredArgsConstructor
public class OverseasStockMasterSyncScheduler {

    private final OverseasStockMasterSyncService overseasStockMasterSyncService;

    @Scheduled(cron = "0 30 3 * * SUN", zone = "Asia/Seoul")
    public void syncOverseasStockMaster() {
        SafeExecutor.runSafely("해외 종목마스터 동기화", overseasStockMasterSyncService::syncAll);
    }
}
