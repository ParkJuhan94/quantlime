package com.quantlab.infra.kind;

import com.quantlab.common.util.ExternalApiInvoker;
import com.quantlab.infra.kind.dto.KindStockInfo;
import com.quantlab.infra.kind.exception.KindApiErrorCode;
import com.quantlab.stock.domain.MarketType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * KIND(kind.krx.co.kr)의 상장법인목록 다운로드는 문서화된 API가 아니라
 * "Content-Type: application/vnd.ms-excel"을 자칭하는 HTML 테이블 응답이다
 * (실제 xls/xlsx 바이너리가 아님). KRX 정보데이터시스템(data.krx.co.kr)의
 * 정식 API는 최근 로그인 세션을 요구하도록 바뀌어 인증 없이 쓸 수 있는
 * 이 경로를 택했다.
 */
@Component
@RequiredArgsConstructor
public class KindApiClient {

    private static final Pattern STANDARD_STOCK_CODE = Pattern.compile("^\\d{6}$");

    private final RestClient kindRestClient;

    public List<KindStockInfo> fetchCorpList(MarketType marketType) {
        return ExternalApiInvoker.call(
            KindApiErrorCode.CORP_LIST_INQUIRY_FAILED,
            () -> {
                String html = kindRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/corpgeneral/corpList.do")
                        .queryParam("method", "download")
                        .queryParam("marketType", toKindMarketParam(marketType))
                        .build())
                    .retrieve()
                    .body(String.class);
                return parseCorpList(html, marketType);
            });
    }

    private List<KindStockInfo> parseCorpList(String html, MarketType marketType) {
        List<KindStockInfo> stocks = new ArrayList<>();
        Elements rows = Jsoup.parse(html).select("table tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            // 헤더 행(th만 있는 tr)이나 형식이 다른 행은 건너뛴다
            if (cells.size() < 4) {
                continue;
            }

            String stockName = cells.get(0).text().trim();
            String stockCode = cells.get(2).text().trim();
            String sector = cells.get(3).text().trim();

            // 스팩·비상장 특수목적법인 등 표준 6자리 숫자 코드가 아닌 행은
            // 실제 거래 가능한 종목이 아니므로 제외한다
            if (!STANDARD_STOCK_CODE.matcher(stockCode).matches()) {
                continue;
            }

            stocks.add(new KindStockInfo(
                stockCode, stockName, marketType, sector.isEmpty() ? "기타" : sector));
        }

        return stocks;
    }

    private String toKindMarketParam(MarketType marketType) {
        return switch (marketType) {
            case KOSPI -> "stockMkt";
            case KOSDAQ -> "kosdaqMkt";
            case KONEX -> "konexMkt";
        };
    }
}
