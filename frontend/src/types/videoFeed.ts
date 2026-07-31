export interface VideoFeedTicker {
  tickerCode: string
  tickerName: string | null
  stance: string
  confidence: number
}

export interface VideoFeedItem {
  videoId: number
  channelName: string
  channelProfileImageUrl: string | null
  channelUrl: string | null
  title: string
  videoUrl: string | null
  publishedAt: string
  durationSec: number | null
  summary: string
  tickers: VideoFeedTicker[]
}

export interface VideoFeedDetail {
  videoId: number
  channelName: string
  channelProfileImageUrl: string | null
  channelUrl: string | null
  title: string
  videoUrl: string | null
  publishedAt: string
  durationSec: number | null
  summary: string
  keyPoints: string[]
  caveat: string
  tickers: VideoFeedTicker[]
}
