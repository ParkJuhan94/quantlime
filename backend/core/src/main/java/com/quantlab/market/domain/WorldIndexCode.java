package com.quantlab.market.domain;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 홈 화면 지수 카드에서 다루는 해외지수 - 프론트/URL에는 사람이 읽기 쉬운
 * 코드(NASDAQ 등)를 쓰고, 네이버 금융 조회에는 로이터 코드(.IXIC 등)가
 * 필요해 그 매핑만 여기 모아둔다. 컨트롤러가 @Pattern으로 이미 코드값을
 * 검증해 여기 도달하는 code는 항상 유효하므로, 매칭 실패는 방어적 예외로만
 * 둔다(정상 흐름에서는 발생하지 않음).
 *
 * SOXX(아이셰어즈 반도체 ETF)는 진짜 지수가 아니라 ETF라 네이버 금융에서도
 * 지수 엔드포인트(/index/...)가 아니라 종목 엔드포인트(/stock/...)로
 * 조회해야 한다(실제 호출로 확인 - reutersCode도 형태가 다름, "SOXX.O"처럼
 * 거래소 접미사가 붙음) - isEtf로 구분해 호출측(MarketIndexCache 등)이
 * 어느 API를 쓸지 분기한다.
 */
@Getter
@RequiredArgsConstructor
public enum WorldIndexCode {

    NASDAQ(".IXIC", false),
    SP500(".INX", false),
    SOXX("SOXX.O", true);

    private final String reutersCode;
    private final boolean etf;

    public static WorldIndexCode from(String code) {
        return Arrays.stream(values())
            .filter(value -> value.name().equalsIgnoreCase(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 해외지수 코드입니다: " + code));
    }
}
