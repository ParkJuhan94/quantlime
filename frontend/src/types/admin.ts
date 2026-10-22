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

export type PriceRepairMarket = 'domestic' | 'overseas' | 'all'

export interface PriceJumpReport {
  stockCode: string
  tradeDate: string
  previousTradeDate: string
  previousClose: number
  close: number
  ratio: number
}

export interface RegularCloseBackfillResult {
  confirmed: number
  skipped: number
  failed: number
}

// ---- 배치 실행 이력(Spring Batch Job) ----

export interface BatchStep {
  name: string
  status: string
  readCount: number
  writeCount: number
  skipCount: number
  commitCount: number
}

// 시각은 서버(KST) 기준 시간대 없는 문자열이다 - utils/batchExecution.ts가 변환 없이 분해해서 보여준다.
// 목록 응답은 steps가 비어 있고, 단건 조회에만 Step 상세가 들어 있다.
export interface BatchExecution {
  executionId: number
  // 이어서 실행한 실행들은 같은 jobInstanceId를 가진다
  jobInstanceId: number
  jobName: string
  status: string
  exitCode: string
  exitDescription: string | null
  createTime: string | null
  startTime: string | null
  endTime: string | null
  steps: BatchStep[]
}
