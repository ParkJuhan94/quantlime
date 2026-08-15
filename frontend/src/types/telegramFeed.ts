// videofeed/types/videoFeed.ts와 구조적으로 동일(Phase 8 P7-F2) - 텔레그램
// 글은 제목/재생시간 개념이 없고 대신 조회수(viewCount)/원문 링크(postUrl)를
// 가진다.
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

export interface TelegramFeedPost {
  telegramPostId: number
  channelName: string
  channelProfileImageUrl: string | null
  channelUrl: string
  postUrl: string
  publishedAt: string
  viewCount: number | null
  summary: string
  tickers: TelegramFeedTicker[]
}

export interface TelegramFeedDetail {
  telegramPostId: number
  channelName: string
  channelProfileImageUrl: string | null
  channelUrl: string
  postUrl: string
  publishedAt: string
  viewCount: number | null
  content: string
  summary: string
  keyPoints: string[]
  macroPoints: string[]
  caveat: string
  tickers: TelegramFeedTicker[]
}
