package com.quantlime.infra.dart;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.dart.dto.DartCorpInfo;
import com.quantlime.infra.dart.exception.DartApiErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * DART(금융감독원 전자공시) OpenAPI - 기업고유번호(corpCode) 목록 조회
 * 전용(2026-09, KIND 상장법인목록 스크래핑을 대체 - kind.krx.co.kr이
 * Akamai WAF로 AWS 등 클라우드 IP 대역을 통째로 차단해 운영 서버에서
 * 영구히 접근 불가해짐, 로컬에서는 재현 안 됨). 정식 공개 API라 이런
 * IP 차단 자체가 없다.
 *
 * <p>corpCode.xml은 다른 DART API와 달리 정상/에러 응답 모두 ZIP으로
 * 내려온다 - 압축을 풀어 XML을 파싱한 뒤 {@code <result><status>}가
 * "000"이 아니면(에러 코드 목록은 개발가이드 참고) 에러로 처리한다.
 *
 * <p>DART는 시장구분(코스피/코스닥) 필드를 주지 않는다 - 신규상장 종목의
 * 시장구분은 호출측(DomesticStockMasterSyncService)이 Toss
 * {@code /api/v1/stocks}로 별도 조회한다.
 */
@Component
@RequiredArgsConstructor
public class DartApiClient {

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("^\\d{6}$");

    private final RestClient dartRestClient;
    private final DartApiProperties properties;

    public List<DartCorpInfo> fetchCorpList() {
        return ExternalApiInvoker.call(
            DartApiErrorCode.CORP_LIST_INQUIRY_FAILED,
            () -> {
                byte[] zipBytes = dartRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/corpCode.xml")
                        .queryParam("crtfc_key", properties.getApiKey())
                        .build())
                    .retrieve()
                    .body(byte[].class);
                return parseCorpList(unzipSingleEntry(zipBytes));
            });
    }

    private byte[] unzipSingleEntry(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                throw new IllegalStateException("DART 응답 ZIP이 비어있습니다.");
            }
            return zis.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("DART 응답 ZIP 압축 해제에 실패했습니다.", e);
        }
    }

    private List<DartCorpInfo> parseCorpList(byte[] xmlBytes) {
        Document document = parseXml(xmlBytes);
        Element root = document.getDocumentElement();

        NodeList statusNodes = root.getElementsByTagName("status");
        if (statusNodes.getLength() > 0 && !"000".equals(text(statusNodes.item(0)))) {
            NodeList messageNodes = root.getElementsByTagName("message");
            String message = messageNodes.getLength() > 0 ? text(messageNodes.item(0)) : "알 수 없는 오류";
            throw new ExternalApiException(DartApiErrorCode.CORP_LIST_INQUIRY_FAILED,
                new IllegalStateException("DART API 에러 응답: " + message));
        }

        List<DartCorpInfo> result = new ArrayList<>();
        NodeList listNodes = root.getElementsByTagName("list");
        for (int i = 0; i < listNodes.getLength(); i++) {
            Element listElement = (Element) listNodes.item(i);
            String stockCode = childText(listElement, "stock_code").trim();
            // 비상장 법인은 stock_code가 빈 태그로 내려온다 - 상장 종목만 남긴다.
            if (!STOCK_CODE_PATTERN.matcher(stockCode).matches()) {
                continue;
            }
            result.add(new DartCorpInfo(
                childText(listElement, "corp_code"),
                childText(listElement, "corp_name"),
                stockCode));
        }
        return result;
    }

    private Document parseXml(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE(외부 엔티티 주입) 방지 - DART가 신뢰할 수 있는 소스이긴 하나,
            // XML 파싱 코드의 기본 보안 관행으로 항상 비활성화한다.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            InputStream inputStream = new ByteArrayInputStream(xmlBytes);
            return factory.newDocumentBuilder().parse(inputStream);
        } catch (Exception e) {
            throw new IllegalStateException("DART 응답 XML 파싱에 실패했습니다.", e);
        }
    }

    private String childText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? text(nodes.item(0)) : "";
    }

    private String text(org.w3c.dom.Node node) {
        return node.getTextContent() != null ? node.getTextContent() : "";
    }
}
