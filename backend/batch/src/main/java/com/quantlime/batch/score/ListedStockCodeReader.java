package com.quantlime.batch.score;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;
import org.springframework.lang.Nullable;

/**
 * 상장 종목 코드를 정렬된 순서로 한 건씩 읽는다. 읽은 위치는 부모 클래스가 ExecutionContext에 저장하므로
 * restart하면 끊긴 지점부터 이어 읽는다.
 *
 * <p>목록은 Step을 열 때 한 번만 불러온다. 실패와 재시작 사이에 상장/폐지가 생기면 위치가 한 칸 어긋나 종목
 * 하나가 중복 처리되거나 빠질 수 있다 - 중복은 스코어 저장이 upsert라 무해하고, 빠진 종목은 다음 /refresh 갭필이 채운다.
 */
public class ListedStockCodeReader extends AbstractItemCountingItemStreamItemReader<String> {

    private final Supplier<List<String>> stockCodeSupplier;
    private List<String> stockCodes = List.of();

    public ListedStockCodeReader(String name, Supplier<List<String>> stockCodeSupplier) {
        this.stockCodeSupplier = stockCodeSupplier;
        setName(name);
    }

    @Override
    protected void doOpen() {
        // 최대 건수는 지정하지 않는다 - 빈 목록(해외 종목 0건 등)에서 setMaxItemCount(0)은 예외이고, 끝은 doRead의 null로 판단한다
        stockCodes = stockCodeSupplier.get().stream().sorted().toList();
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
        stockCodes = List.of();
    }
}
