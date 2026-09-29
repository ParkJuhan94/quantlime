package com.quantlime.telegramfeed.event;

import java.time.LocalDate;

/**
 * 채널×날짜 단위 다이제스트 생성을 요청하는 순수 도메인 이벤트(2026-09-30,
 * 카프카 다도메인 확장 Phase 4) - core는 Kafka를 모른다(videofeed/market/
 * subscription/payment와 동일한 원칙). 트리거 시점은 기존과 동일하게 하루
 * 3회(08:30/13:30/20:30) 스케줄 그대로 유지한다 - Gemini 호출이 유튜브와
 * 쿼터를 공유해 재생성 빈도를 늘릴 수 없기 때문(TelegramDigestGenerationScheduler
 * 주석 참고). 채널별로 이벤트를 나눠 발행해 Kafka 재시도/DLT로 채널 단위
 * 장애 격리를 얻는 것이 이번 이벤트화의 목적이지, 트리거 빈도를 바꾸는 게
 * 아니다.
 */
public record TelegramDigestGenerationRequestedEvent(Long channelId, LocalDate date) {
}
