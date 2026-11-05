package com.quantlime.infra.toss.realtime;

import java.util.List;
import java.util.Map;

/** 맺어진 연결 하나. 전송 메서드는 한 스레드에서 차례로 호출해야 한다. */
public interface TossRealtimeSession {

    /** 현재 구독 전체를 이 값으로 교체한다(선언형 full-replace). */
    void declareTrades(Map<TossRealtimeMarket, List<String>> codesByMarket);

    void ping();

    boolean isOpen();

    void close();
}
