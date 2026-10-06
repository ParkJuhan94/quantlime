package com.quantlime.stock.domain;

import java.util.regex.Pattern;

/**
 * 가격 소스(Toss)가 받아주는 종목 코드 형식 규칙. 해외 종목마스터 등록과 기존 종목 재검증이
 * 같은 기준을 써야 해 한곳에 둔다.
 *
 * <p>Stock.stockCode 컬럼이 length=10(2026-08-01 6→10 완화)이라 이를 넘는 심볼은 등록 대상에서
 * 제외하고, Toss 심볼 파라미터가 허용하는 문자 집합(toss-openapi.json의 symbols 패턴)을 벗어난
 * "AAC/UN"·"ABR/F" 같은 SPAC 유닛/우선주 표기는 길이와 무관하게 Toss가 항상 400으로 거부한다는
 * 게 실측으로 확인돼 등록 자체를 막는다.
 */
public final class TossSymbolPolicy {

    public static final int MAX_STOCK_CODE_LENGTH = 10;

    private static final Pattern TOSS_SYMBOL_PATTERN = Pattern.compile("^[A-Za-z0-9.,-]+$");

    private TossSymbolPolicy() {
    }

    public static boolean isTooLong(String symbol) {
        return symbol.length() > MAX_STOCK_CODE_LENGTH;
    }

    public static boolean isSupportedFormat(String symbol) {
        return TOSS_SYMBOL_PATTERN.matcher(symbol).matches();
    }
}
