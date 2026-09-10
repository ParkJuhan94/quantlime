// videofeed/types/videoFeed.ts와 구조적으로 대응하되(Phase 8 P7-F2), 텔레그램
// 요약은 글 1건이 아니라 채널×날짜 단위 다이제스트다(2026-08-15 재설계 -
// 하루 수십 건씩 올라오는 채널을 개별 요약하면 정보 밀도가 낮다는 판단).
export interface TelegramFeedChannel {
  channelId: number
  name: string
}

export interface TelegramFeedTicker {
  tickerCode: string
  tickerName: string | null
  stance: string
  confidence: number
}

export interface TelegramFeedDigest {
  telegramDigestId: number
  channelName: string
  channelProfileImageUrl: string | null
  channelUrl: string
  digestDate: string
  // 다이제스트는 하루 3회(08:30/13:30/20:30) upsert로 계속 덮어써진다 -
  // 몇 시 기준 최신본인지 보여주기 위한 최소 대응(리뷰 세션 Q2, 2026-09-10).
  updatedAt: string
  sourcePostCount: number
  summary: string
  tickers: TelegramFeedTicker[]
}

export interface TelegramFeedDigestDetail {
  telegramDigestId: number
  channelName: string
  channelProfileImageUrl: string | null
  channelUrl: string
  digestDate: string
  updatedAt: string
  // 다이제스트가 여러 글을 합친 결과라 원문이 하나가 아니다 - 그날 재료가
  // 된 글의 원문 링크 목록(발행시각순).
  sourcePostUrls: string[]
  summary: string
  keyPoints: string[]
  macroPoints: string[]
  caveat: string
  tickers: TelegramFeedTicker[]
}
