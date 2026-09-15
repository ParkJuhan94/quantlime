package com.quantlime.stock.service;

import com.quantlime.infra.dart.DartApiClient;
import com.quantlime.infra.dart.dto.DartCorpInfo;
import com.quantlime.infra.toss.TossApiClient;
import com.quantlime.infra.toss.dto.TossStockInfoResponse;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.dto.StockMasterSyncResult;
import com.quantlime.stock.repository.StockRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2026-09 KIND(kind.krx.co.kr) 스크래핑 → DART OpenAPI 전환 - KIND가
 * Akamai WAF로 AWS 등 클라우드 IP 대역을 차단해 운영 서버에서 영구히
 * 접근 불가해짐(로컬에서는 재현 안 됨, "IP 평판" 문제라 헤더/클라이언트를
 * 바꿔도 해결 안 됐음). DART는 정식 공개 API라 이런 차단이 없다.
 *
 * <p>DART corpCode 목록에는 시장구분(코스피/코스닥) 필드가 없다 - 신규
 * 상장 종목만 Toss {@code /api/v1/stocks}로 별도 조회한다(기존 종목은
 * 이미 우리 DB에 시장구분이 있으므로 재조회 불필요, 호출량이 작음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticStockMasterSyncService {

    private static final int TOSS_STOCK_INFO_BATCH_SIZE = 200;

    private final DartApiClient dartApiClient;
    private final TossApiClient tossApiClient;
    private final StockRepository stockRepository;

    @Transactional
    public StockMasterSyncResult syncStockMaster() {
        Map<String, DartCorpInfo> latestByCode = fetchLatestCorpList();
        Map<String, Stock> existingByCode = stockRepository.findAll().stream()
            .collect(Collectors.toMap(Stock::getStockCode, Function.identity()));

        int newlyListedCount = registerNewlyListed(latestByCode, existingByCode);
        int delistedCount = markDelisted(latestByCode, existingByCode);

        log.info("종목마스터 동기화 완료: 신규상장={}, 상장폐지={}",
            newlyListedCount, delistedCount);
        return new StockMasterSyncResult(newlyListedCount, delistedCount);
    }

    private Map<String, DartCorpInfo> fetchLatestCorpList() {
        return dartApiClient.fetchCorpList().stream()
            // 동일 종목코드가 중복 행으로 내려오는 경우 먼저 잡힌 값을 유지한다
            // (KIND 시절과 동일한 방어 - DART에서 실제로 겪은 적은 없지만
            // 외부 응답을 신뢰하지 않는 관행을 유지).
            .collect(Collectors.toMap(DartCorpInfo::stockCode, Function.identity(), (a, b) -> a));
    }

    private int registerNewlyListed(
        Map<String, DartCorpInfo> latestByCode, Map<String, Stock> existingByCode) {
        List<String> newCodes = latestByCode.keySet().stream()
            .filter(code -> !existingByCode.containsKey(code))
            .toList();
        if (newCodes.isEmpty()) {
            return 0;
        }

        Map<String, MarketType> marketByCode = resolveMarketTypes(newCodes);
        int count = 0;
        for (String code : newCodes) {
            MarketType marketType = marketByCode.get(code);
            if (marketType == null) {
                // 코넥스 등 Toss가 KOSPI/KOSDAQ로 분류하지 않는 시장이거나
                // Toss 조회 자체가 실패한 경우 - 잘못된 시장구분으로 등록하지
                // 않고 다음 동기화(내일 배치)에서 다시 시도되게 스킵한다.
                log.warn("종목마스터 동기화: 시장구분 확인 불가로 신규 등록 스킵: stockCode={}, name={}",
                    code, latestByCode.get(code).corpName());
                continue;
            }
            DartCorpInfo info = latestByCode.get(code);
            // DART corpCode API는 업종(sector) 정보를 주지 않는다 - KIND
            // 시절 빈 값 폴백과 동일하게 "기타"로 남긴다(없는 데이터를
            // 지어내지 않음).
            stockRepository.save(Stock.of(code, info.corpName(), marketType, ListingStatus.LISTED, "기타"));
            count++;
        }
        return count;
    }

    private Map<String, MarketType> resolveMarketTypes(List<String> codes) {
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
            // 코넥스(KONEX)는 Toss market enum에 없다 - 별도 분류값이 없어
            // 코넥스 신규상장은 이 경로로 자동 등록되지 않는다(위 registerNewlyListed
            // 경고 로그로 드러남, 발견 시 수동 등록 검토).
            default -> Optional.empty();
        };
    }

    /**
     * DART는 국내(KOSPI/KOSDAQ/코넥스) 전용 소스라 해외종목은 latestByCode에
     * 애초에 존재할 수 없다 - 시장 구분 없이 전체 종목을 비교하면 모든
     * 해외종목이 "DART 목록에서 사라졌다"고 오판돼 매 동기화마다 DELISTED로
     * 잘못 표시된다(KIND 시절 실제로 겪었던 버그, 동일하게 방어).
     */
    private int markDelisted(
        Map<String, DartCorpInfo> latestByCode, Map<String, Stock> existingByCode) {
        int count = 0;
        for (Stock stock : existingByCode.values()) {
            if (!stock.getMarketType().isDomestic()) {
                continue;
            }
            boolean stillListed = latestByCode.containsKey(stock.getStockCode());
            if (stock.getListingStatus() == ListingStatus.LISTED && !stillListed) {
                stock.updateListingStatus(ListingStatus.DELISTED);
                count++;
            }
        }
        return count;
    }
}
