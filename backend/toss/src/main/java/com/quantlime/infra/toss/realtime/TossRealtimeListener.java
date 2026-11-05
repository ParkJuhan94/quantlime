package com.quantlime.infra.toss.realtime;

import java.util.List;

/** 연결 하나의 수신 이벤트. HTTP 클라이언트 스레드에서 호출되므로 오래 막지 말 것. */
public interface TossRealtimeListener {

    void onTrade(TossRealtimeTrade trade);

    /** 구독 선언 ack. {@code rejectedTargets}는 {@code trade:kr:005930} 형식이다. */
    void onSubscriptions(int subscribedCount, List<String> rejectedTargets);

    /** 선언 실패나 서버 공지. {@code auth-revoked}·{@code server-shutdown}은 직후 연결이 닫힌다. */
    void onError(String code, String message);

    void onPong();

    void onClosed(String reason);
}
