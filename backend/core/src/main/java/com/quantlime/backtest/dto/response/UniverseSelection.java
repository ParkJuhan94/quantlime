package com.quantlime.backtest.dto.response;

import java.util.ArrayList;
import java.util.List;

/**
 * 백테스트 데이터셋 준비가 선정한 유니버스(국내/해외 종목 코드). 국내 먼저, 해외 나중 순서로 합쳐 쓸 수 있도록
 * {@link #all()}을 제공한다.
 */
public record UniverseSelection(List<String> domestic, List<String> overseas) {

    public List<String> all() {
        List<String> all = new ArrayList<>(domestic.size() + overseas.size());
        all.addAll(domestic);
        all.addAll(overseas);
        return all;
    }
}
