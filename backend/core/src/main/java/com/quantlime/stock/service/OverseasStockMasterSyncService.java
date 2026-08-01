package com.quantlime.stock.service;

import com.quantlime.infra.kis.KisOverseasStockMasterClient;
import com.quantlime.infra.kis.dto.KisOverseasStockMasterEntry;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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

    private static final Map<MarketType, String> EXCHANGE_CODE = Map.of(
        MarketType.NASDAQ, "nas",
        MarketType.NYSE, "nys",
        MarketType.AMEX, "ams"
    );
    // Stock.stockCode 컬럼이 length=10(2026-08-01 6→10 완화 - Score/
    // DomesticDailyPrice/OverseasDailyPrice/BacktestDailyScore/BacktestResult도
    // 동일하게 맞춤)이라, 이를 넘는 해외 심볼은 등록 대상에서 제외한다.
    // 다만 "/"가 섞인 SPAC 유닛 표기(예: "XFLH/UN")는 길이를 늘려도 여전히
    // 아래 TOSS_SYMBOL_PATTERN에 걸려 제외된다 - Toss API 자체가 "/"를
    // 400으로 거부하는 실제 제약이라 로컬 컬럼 폭과 무관하다. 이번 완화의
    // 실질 효과는 "/" 없이 순수하게 6자를 넘는 심볼이 새로 저장 가능해지는
    // 것뿐이다.
    private static final int MAX_STOCK_CODE_LENGTH = 10;
    // Toss 심볼 파라미터가 허용하는 문자 집합(toss-openapi.json의 symbols
    // 패턴과 동일) - "AAC/UN"·"ABR/F"처럼 "/"가 섞인 SPAC 유닛/우선주
    // 표기는 길이 제한(6자)만으로는 안 걸러지는데(둘 다 6자 이하), Toss가
    // 이런 심볼을 항상 400으로 거부한다는 게 실측으로 확인됐다
    // (MarketDataRefreshService.isUnsupportedSymbolFormat 참고) - 등록
    // 자체를 막아 애초에 가격 조회 시도가 발생하지 않게 한다.
    private static final Pattern TOSS_SYMBOL_PATTERN = Pattern.compile("^[A-Za-z0-9.,-]+$");

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
        int skippedInvalidFormat = 0;
        for (KisOverseasStockMasterEntry entry : entries) {
            if (!entry.isStock()) {
                continue;
            }
            if (entry.symbol().length() > MAX_STOCK_CODE_LENGTH) {
                skippedTooLong++;
                continue;
            }
            if (!TOSS_SYMBOL_PATTERN.matcher(entry.symbol()).matches()) {
                skippedInvalidFormat++;
                continue;
            }
            stockMasterService.registerStock(
                entry.symbol(), entry.englishName(), marketType, entry.industryCode(), entry.koreanName());
            registered++;
        }
        int markedUnsupported = markExistingInvalidFormatStocksUnsupported(marketType);
        log.info("해외 종목마스터 동기화 완료: marketType={}, 전체={}건, 등록시도={}건, "
                + "코드길이초과스킵={}건, Toss미지원형식스킵={}건, 기존종목중미지원표시={}건",
            marketType, entries.size(), registered, skippedTooLong, skippedInvalidFormat, markedUnsupported);
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
            if (!TOSS_SYMBOL_PATTERN.matcher(stock.getStockCode()).matches()) {
                stockMasterService.markPriceUnsupported(stock.getStockCode());
                marked++;
            }
        }
        return marked;
    }
}
