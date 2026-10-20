export interface AdminFeedPost {
  id: number
  nickname: string
  category: string
  title: string
  imageUrl: string | null
  reportCount: number
  createdAt: string
}

export interface AdminFeedComment {
  id: number
  postId: number
  nickname: string
  content: string
  reportCount: number
  createdAt: string
}

// 영상·텔레그램 수집 관리에서 쓰는 채널 요약(백엔드 응답의 필요한 필드만)
export interface AdminChannel {
  channelId: number
  name: string
  enabled: boolean
  priority: number
  lastCollectedAt: string | null
  // 영상 채널에만 있다(텔레그램은 없음)
  medianVelocity?: number | null
}

export interface CollectResult {
  channelName: string
  discoveredCount: number
  success: boolean
  errorMessage: string | null
}

export interface TelegramRetentionResult {
  deletedPostCount: number
  deletedDigestCount: number
}

export type BacktestMarket = 'KOSPI' | 'KOSDAQ' | 'NASDAQ' | 'NYSE'

export interface CrossSectionalParams {
  market?: BacktestMarket
  scoreVersion: string
  nullTest: boolean
  nullRepeats: number
}
