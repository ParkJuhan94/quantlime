package com.quantlime.stock.implement;

import com.quantlime.infra.kis.KisOverseasStockMasterClient;
import com.quantlime.infra.kis.dto.KisOverseasStockMasterEntry;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.TossSymbolPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * KIS 해외주식 종목정보 마스터파일을 받아 등록 가능한 종목만 골라내는 수집 흐름. 서비스는 KIS
 * 클라이언트·거래소 코드·파일 항목 해석을 몰라도 되고, 골라진 종목을 어떻게 등록·정리할지만 결정한다.
 */
@Component
@RequiredArgsConstructor
public class OverseasStockMasterCollector {

    private static final Map<MarketType, String> EXCHANGE_CODE = Map.of(
        MarketType.NASDAQ, "nas",
        MarketType.NYSE, "nys",
        MarketType.AMEX, "ams"
    );

    private final KisOverseasStockMasterClient kisOverseasStockMasterClient;

    /** 동기화를 지원하는 해외 시장. */
    public Set<MarketType> supportedMarkets() {
        return EXCHANGE_CODE.keySet();
    }

    /**
     * 시장의 마스터파일을 받아 보통주만, 코드 길이·Toss 심볼 형식 규칙을 통과한 것만 돌려준다.
     *
     * @throws IllegalArgumentException 동기화를 지원하지 않는 시장
     */
    public Fetched fetchRegistrable(MarketType marketType) {
        String exchangeCode = EXCHANGE_CODE.get(marketType);
        if (exchangeCode == null) {
            throw new IllegalArgumentException("해외 종목마스터 동기화 미지원 시장: " + marketType);
        }
        List<KisOverseasStockMasterEntry> entries = kisOverseasStockMasterClient.fetchStockMaster(exchangeCode);
        List<KisOverseasStockMasterEntry> registrable = new ArrayList<>();
        int skippedTooLong = 0;
        int skippedInvalidFormat = 0;
        for (KisOverseasStockMasterEntry entry : entries) {
            if (!entry.isStock()) {
                continue;
            }
            if (TossSymbolPolicy.isTooLong(entry.symbol())) {
                skippedTooLong++;
                continue;
            }
            if (!TossSymbolPolicy.isSupportedFormat(entry.symbol())) {
                skippedInvalidFormat++;
                continue;
            }
            registrable.add(entry);
        }
        return new Fetched(entries.size(), registrable, skippedTooLong, skippedInvalidFormat);
    }

    public record Fetched(
        int totalCount, List<KisOverseasStockMasterEntry> registrable, int skippedTooLong, int skippedInvalidFormat) {
    }
}
