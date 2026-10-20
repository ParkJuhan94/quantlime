package com.quantlime.notification.domain;

/**
 * 알림에 딸린 종목 참조(랭킹·사분면 변화 알림). 화면이 종목명을 눌러 종목 상세(/stocks/{code})로 보내는 데 쓴다.
 * notification.stocks JSON 컬럼에 그대로 저장되므로 필드를 바꾸면 이미 저장된 행과의 호환을 같이 고려해야 한다.
 */
public record NotificationStock(String code, String name) {
}
