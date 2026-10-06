package com.quantlime.stock.implement;

import com.quantlime.infra.dart.DartApiClient;
import com.quantlime.infra.dart.dto.DartCorpInfo;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossStockInfoResponse;
import com.quantlime.stock.domain.MarketType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 국내 종목마스터 동기화에 필요한 외부 조회(DART 상장법인 목록, Toss 시장구분)를 감싸는 수집 흐름.
 * 서비스는 어떤 외부 API를 어떻게 부르는지 몰라도 되고 "신규상장/상장폐지를 어떻게 반영할지"만 결정한다.
 */
@Component
@RequiredArgsConstructor
public class DomesticStockMasterCollector {

    private static final int TOSS_STOCK_INFO_BATCH_SIZE = 200;

    private final DartApiClient dartApiClient;
    private final TossApiClient tossApiClient;

    /** 종목코드 → DART 상장법인 정보. 같은 코드가 중복 행으로 내려오면 먼저 잡힌 값을 유지한다. */
    public Map<String, DartCorpInfo> fetchLatestCorpList() {
        return dartApiClient.fetchCorpList().stream()
            // 외부 응답을 신뢰하지 않는 관행 - DART에서 실제로 겪은 적은 없지만 KIND 시절 방어를 유지
            .collect(Collectors.toMap(DartCorpInfo::stockCode, Function.identity(), (a, b) -> a));
    }

    /**
     * 신규 후보 종목의 시장구분(KOSPI/KOSDAQ)을 Toss로 조회한다. Toss가 취급하지 않는 종목(코넥스·
     * 우선주·SPAC 등)은 결과에 없다 - 잘못된 시장구분으로 등록하지 않고 다음 동기화에서 재시도되게 둔다.
     */
    public Map<String, MarketType> resolveMarketTypes(List<String> codes) {
        Map<String, MarketType> result = new HashMap<>();
        for (int i = 0; i < codes.size(); i += TOSS_STOCK_INFO_BATCH_SIZE) {
            List<String> chunk = codes.subList(i, Math.min(i + TOSS_STOCK_INFO_BATCH_SIZE, codes.size()));
            TossStockInfoResponse response = tossApiClient.getStockInfo(String.join(",", chunk));
            for (TossStockInfoResponse.TossStockInfo info : response.result()) {
                toDomesticMarketType(info.market()).ifPresent(marketType -> result.put(info.symbol(), marketType));
            }
        }
        return result;
    }

    private Optional<MarketType> toDomesticMarketType(String tossMarket) {
        return switch (tossMarket) {
            case "KOSPI" -> Optional.of(MarketType.KOSPI);
            case "KOSDAQ" -> Optional.of(MarketType.KOSDAQ);
            // 코넥스(KONEX)는 Toss market enum에 없다 - 코넥스 신규상장은 이 경로로 자동 등록되지 않는다
            default -> Optional.empty();
        };
    }
}
