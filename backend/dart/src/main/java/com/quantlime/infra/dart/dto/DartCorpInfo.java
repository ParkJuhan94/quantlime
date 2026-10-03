package com.quantlime.infra.dart.dto;

/**
 * DART corpCode.xml 응답의 {@code <list>} 항목 하나. {@code stockCode}가
 * 빈 문자열인 항목(비상장 법인)은 {@link com.quantlime.infra.dart.DartApiClient}가
 * 미리 걸러내므로 여기 도달하는 건 전부 상장 종목이다.
 */
public record DartCorpInfo(
    String corpCode,
    String corpName,
    String stockCode
) {
}
