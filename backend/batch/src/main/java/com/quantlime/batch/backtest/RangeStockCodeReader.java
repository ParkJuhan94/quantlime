package com.quantlime.batch.backtest;

import java.util.List;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;
import org.springframework.lang.Nullable;

/**
 * 유니버스 종목 코드 목록의 [start, end) 구간을 순서대로 한 건씩 읽는다. 목록 순서는 데이터셋 준비 Step이 정한 것(거래대금
 * 순)을 그대로 유지한다. 읽은 위치는 부모 클래스가 ExecutionContext에 저장하므로 restart하면 끊긴 종목부터 이어 읽는다.
 */
public class RangeStockCodeReader extends AbstractItemCountingItemStreamItemReader<String> {

    private final List<String> stockCodes;

    public RangeStockCodeReader(String name, List<String> universe, int start, int end) {
        int from = Math.max(0, Math.min(start, universe.size()));
        int to = Math.max(from, Math.min(end, universe.size()));
        this.stockCodes = List.copyOf(universe.subList(from, to));
        setName(name);
    }

    @Override
    protected void doOpen() {
        // 최대 건수는 지정하지 않는다 - 빈 구간에서 setMaxItemCount(0)은 예외이고, 끝은 doRead의 null로 판단한다
    }

    @Override
    @Nullable
    protected String doRead() {
        // 부모가 읽은 건수를 센다 - currentItemCount는 이미 증가한 뒤라 1을 뺀 위치가 이번 항목이다
        int index = getCurrentItemCount() - 1;
        return index < stockCodes.size() ? stockCodes.get(index) : null;
    }

    @Override
    protected void doClose() {
        // 보유 자원 없음
    }
}
