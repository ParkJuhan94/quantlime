package com.quantlime.stock.implement;

import com.quantlime.infra.naver.NaverFinanceApiClient;
import com.quantlime.infra.naver.dto.NaverStockFinanceAnnualResponse;
import com.quantlime.infra.naver.dto.NaverStockIntegrationResponse;
import com.quantlime.stock.dto.response.StockFundamentalsResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 네이버 금융에서 종목 밸류에이션 지표(시총/PER/PBR/PSR/부채비율)를 수집·파싱한다.
 * 캐싱(TTL)은 {@code StockFundamentalsCache}가 맡고, 여기는 외부 응답을 도메인 응답으로
 * 바꾸는 일만 한다. 한쪽 호출이 실패해도 그 필드만 null로 두고 나머지는 채운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockFundamentalsCollector {

    private static final Pattern TRILLION_PATTERN = Pattern.compile("([\\d,]+)조");
    private static final Pattern BILLION_PATTERN = Pattern.compile("([\\d,]+)억");

    private final NaverFinanceApiClient naverFinanceApiClient;

    public StockFundamentalsResponse collect(String stockCode) {
        Double marketCap = null;
        Double per = null;
        Double forwardPer = null;
        Double pbr = null;
        Double debtRatio = null;
        Double revenue = null;

        try {
            NaverStockIntegrationResponse integration = naverFinanceApiClient.getStockIntegration(stockCode);
            for (NaverStockIntegrationResponse.TotalInfo info : integration.totalInfos()) {
                switch (info.code()) {
                    case "marketValue" -> marketCap = parseKoreanWon(info.value());
                    case "per" -> per = parseRatio(info.value());
                    case "cnsPer" -> forwardPer = parseRatio(info.value());
                    case "pbr" -> pbr = parseRatio(info.value());
                    default -> { }
                }
            }
        } catch (Exception e) {
            log.warn("네이버 금융 종목 통합정보 조회 실패: stockCode={}, error={}", stockCode, e.getMessage());
        }

        try {
            NaverStockFinanceAnnualResponse finance = naverFinanceApiClient.getStockFinanceAnnual(stockCode);
            String latestActualYearKey = latestActualYearKey(finance);
            if (latestActualYearKey != null) {
                debtRatio = findRowValue(finance, "부채비율", latestActualYearKey)
                    .map(this::parseRatio)
                    .orElse(null);
                // 매출액은 "억원" 단위로 내려온다 - 처음엔 "백만원"으로
                // 잘못 가정해 PSR이 100배 부풀려졌었다(삼성전자 PSR이 489로
                // 나와 실측 대조 중 발견 - 실제 삼성전자 연간 매출은 300조원대라
                // 3,336,059를 백만원 단위로 보면 3.3조원에 불과해 말이 안 됨.
                // 억원 단위(x1억)로 계산하면 333.6조원으로 실제 규모와 맞고
                // PSR도 4.9 수준의 합리적인 값이 나옴 - 실제 호출 결과로 검증).
                revenue = findRowValue(finance, "매출액", latestActualYearKey)
                    .map(this::parseRatio)
                    .map(value -> value * 100_000_000)
                    .orElse(null);
            }
        } catch (Exception e) {
            log.warn("네이버 금융 재무제표 조회 실패: stockCode={}, error={}", stockCode, e.getMessage());
        }

        Double psr = (marketCap != null && revenue != null && revenue > 0) ? marketCap / revenue : null;

        return new StockFundamentalsResponse(marketCap, per, forwardPer, pbr, psr, debtRatio);
    }

    /** trTitleList는 연도 오름차순으로 내려온다 - isConsensus="N"(확정치) 중 가장 최근 연도를 쓴다. */
    private String latestActualYearKey(NaverStockFinanceAnnualResponse finance) {
        if (finance.financeInfo() == null) return null;
        List<NaverStockFinanceAnnualResponse.TrTitle> titles = finance.financeInfo().trTitleList();
        if (titles == null) return null;
        String result = null;
        for (NaverStockFinanceAnnualResponse.TrTitle title : titles) {
            if ("N".equalsIgnoreCase(title.isConsensus())) {
                result = title.key();
            }
        }
        return result;
    }

    private java.util.Optional<String> findRowValue(NaverStockFinanceAnnualResponse finance, String rowTitle, String yearKey) {
        if (finance.financeInfo() == null || finance.financeInfo().rowList() == null) {
            return java.util.Optional.empty();
        }
        return finance.financeInfo().rowList().stream()
            .filter(row -> rowTitle.equals(row.title()))
            .findFirst()
            .map(NaverStockFinanceAnnualResponse.Row::columns)
            .map(columns -> columns.get(yearKey))
            .map(NaverStockFinanceAnnualResponse.ColumnValue::value)
            .filter(value -> value != null && !"-".equals(value));
    }

    /** "1,634조 349억" 같은 한국어 단위 표기를 원 단위 숫자로 변환한다. */
    private double parseKoreanWon(String raw) {
        double result = 0;
        Matcher trillion = TRILLION_PATTERN.matcher(raw);
        if (trillion.find()) {
            result += Double.parseDouble(trillion.group(1).replace(",", "")) * 1_000_000_000_000L;
        }
        Matcher billion = BILLION_PATTERN.matcher(raw);
        if (billion.find()) {
            result += Double.parseDouble(billion.group(1).replace(",", "")) * 100_000_000L;
        }
        return result;
    }

    /** "22.59배", "29.94", "-" 등을 숫자로 변환한다("-"·빈값은 null). */
    private Double parseRatio(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return null;
        String cleaned = raw.replace(",", "").replace("배", "").replace("%", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
