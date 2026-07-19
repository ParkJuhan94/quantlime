package com.quantlab.stock.service;

import com.quantlab.infra.kis.KisOverseasStockMasterClient;
import com.quantlab.infra.kis.dto.KisOverseasStockMasterEntry;
import com.quantlab.stock.domain.MarketType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * KIS 해외주식 종목정보 마스터파일로 해외 종목 마스터(현재는 NASDAQ/NYSE만,
 * CLAUDE.md 백테스트 계획 Phase A/C 참고)를 등록한다. 국내 KIND 동기화
 * (StockMasterSyncService)와 달리 상장폐지 감지는 하지 않는다(v1 스코프 -
 * 해외 유니버스는 거래대금 랭킹으로 매번 다시 뽑으므로, 사라진 종목은
 * 자연스럽게 다음 랭킹에서 제외될 뿐 별도 삭제 처리가 필요 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasStockMasterSyncService {

    private static final Map<MarketType, String> EXCHANGE_CODE = Map.of(
        MarketType.NASDAQ, "nas",
        MarketType.NYSE, "nys"
    );
    // Stock.stockCode 컬럼이 국내 6자리 코드 기준 length=6이라, 이를 넘는
    // 해외 심볼(예: SPAC 유닛 표기 "XFLH/UN")은 등록 대상에서 제외한다
    // (실제 마스터파일 확인 결과 NYSE 기준 전체의 약 1.4%, 유동성 낮은
    // 예외적 종목이라 국내 컬럼을 넓히는 것보다 스킵이 최소 침습적).
    private static final int MAX_STOCK_CODE_LENGTH = 6;

    private final KisOverseasStockMasterClient kisOverseasStockMasterClient;
    private final StockMasterService stockMasterService;

    public void syncAll() {
        for (MarketType marketType : EXCHANGE_CODE.keySet()) {
            syncMarket(marketType);
        }
    }

    public void syncMarket(MarketType marketType) {
        String exchangeCode = EXCHANGE_CODE.get(marketType);
        if (exchangeCode == null) {
            throw new IllegalArgumentException("해외 종목마스터 동기화 미지원 시장: " + marketType);
        }

        List<KisOverseasStockMasterEntry> entries = kisOverseasStockMasterClient.fetchStockMaster(exchangeCode);
        int registered = 0;
        int skippedTooLong = 0;
        for (KisOverseasStockMasterEntry entry : entries) {
            if (!entry.isStock()) {
                continue;
            }
            if (entry.symbol().length() > MAX_STOCK_CODE_LENGTH) {
                skippedTooLong++;
                continue;
            }
            stockMasterService.registerStock(entry.symbol(), entry.englishName(), marketType, null);
            registered++;
        }
        log.info("해외 종목마스터 동기화 완료: marketType={}, 전체={}건, 등록시도={}건, 코드길이초과스킵={}건",
            marketType, entries.size(), registered, skippedTooLong);
    }
}
