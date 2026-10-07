package com.quantlime.stock.service;

import com.quantlime.infra.kis.dto.KisOverseasStockMasterEntry;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.domain.TossSymbolPolicy;
import com.quantlime.stock.implement.OverseasStockMasterCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * KIS 해외주식 종목정보 마스터파일로 해외 종목 마스터(NASDAQ/NYSE/AMEX,
 * CLAUDE.md 백테스트 계획 Phase A/C 참고 - AMEX는 2026-08-01 추가)를
 * 등록한다. 국내 KIND 동기화(DomesticStockMasterSyncService)와 달리 상장폐지
 * 감지는 하지 않는다(v1 스코프 - 해외 유니버스는 거래대금 랭킹으로 매번
 * 다시 뽑으므로, 사라진 종목은 자연스럽게 다음 랭킹에서 제외될 뿐 별도
 * 삭제 처리가 필요 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasStockMasterSyncService {

    private final OverseasStockMasterCollector overseasStockMasterCollector;
    private final StockMasterService stockMasterService;

    public void syncAll() {
        for (MarketType marketType : overseasStockMasterCollector.supportedMarkets()) {
            syncMarket(marketType);
        }
    }

    public void syncMarket(MarketType marketType) {
        OverseasStockMasterCollector.Fetched fetched = overseasStockMasterCollector.fetchRegistrable(marketType);
        for (KisOverseasStockMasterEntry entry : fetched.registrable()) {
            stockMasterService.registerStock(
                entry.symbol(), entry.englishName(), marketType, entry.industryCode(), entry.koreanName());
        }
        int markedUnsupported = markExistingInvalidFormatStocksUnsupported(marketType);
        log.info("해외 종목마스터 동기화 완료: marketType={}, 전체={}건, 등록시도={}건, "
                + "코드길이초과스킵={}건, Toss미지원형식스킵={}건, 기존종목중미지원표시={}건",
            marketType, fetched.totalCount(), fetched.registrable().size(), fetched.skippedTooLong(),
            fetched.skippedInvalidFormat(), markedUnsupported);
    }

    /**
     * 위 신규 등록 필터를 추가하기 전에 이미 저장된 종목(예: "AAC/UN") 중
     * Toss가 거부하는 형식이 섞여 있어, 매 동기화마다 재검증해 있다면
     * price_unsupported로 표시한다 - 이걸 안 하면 매 기동 가격 갱신
     * 스윕에서 같은 400 실패가 계속 반복된다(2026-07-30, 실제 로그로
     * 확인된 무한 반복 버그).
     */
    private int markExistingInvalidFormatStocksUnsupported(MarketType marketType) {
        int marked = 0;
        for (Stock stock : stockMasterService.getAllListedStocks()) {
            if (stock.getMarketType() != marketType || stock.isPriceUnsupported()) {
                continue;
            }
            if (!TossSymbolPolicy.isSupportedFormat(stock.getStockCode())) {
                stockMasterService.markPriceUnsupported(stock.getStockCode());
                marked++;
            }
        }
        return marked;
    }
}
