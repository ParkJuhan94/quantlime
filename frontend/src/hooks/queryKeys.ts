export const queryKeys = {
  watchlist: ['watchlist'] as const,
  watchlistGroups: ['watchlist', 'groups'] as const,
  me: ['me'] as const,
  linkedProviders: ['me', 'linked-providers'] as const,
  stockSearch: (q: string, page: number) => ['stocks', 'search', q, page] as const,
  stockDetail: (stockCode: string) => ['stocks', 'detail', stockCode] as const,
  stockPrice: (stockCode: string) => ['stocks', 'price', stockCode] as const,
  stockOrderbook: (stockCode: string) => ['stocks', 'orderbook', stockCode] as const,
  stockTrades: (stockCode: string) => ['stocks', 'trades', stockCode] as const,
  stockPriceLimit: (stockCode: string) => ['stocks', 'price-limit', stockCode] as const,
  stockWarnings: (stockCode: string) => ['stocks', 'warnings', stockCode] as const,
  stockMinuteChart: (stockCode: string) => ['stocks', 'minute-chart', stockCode] as const,
  stockChart: (stockCode: string, days: number) => ['stocks', 'chart-history', stockCode, days] as const,
  stockInvestorTrading: (stockCode: string, count: number) => ['stocks', 'investor-trading', stockCode, count] as const,
  stockScore: (stockCode: string) => ['stocks', 'score', stockCode] as const,
  backtest: (stockCode: string) => ['backtest', stockCode] as const,
  stockFundamentals: (stockCode: string) => ['stocks', 'fundamentals', stockCode] as const,
  popularStocks: (limit: number) => ['stocks', 'popular', limit] as const,
  // 관리자 배치 실행 이력 - 목록 접두 키로 limit과 무관하게 한 번에 무효화한다(이어서 실행 직후)
  adminBatchExecutionsAll: ['admin', 'batch-executions'] as const,
  adminBatchExecutions: (limit: number) => ['admin', 'batch-executions', limit] as const,
  adminBatchExecution: (executionId: number) => ['admin', 'batch-execution', executionId] as const,
  // watchlistOnly/limit 조합별로 쿼리키가 갈라지는데(useDashboardScores),
  // 관심종목 등록/해제 후에는 조합과 무관하게 전부 무효화해야 해 접두
  // 키를 따로 둔다(React Query는 배열 접두사로 부분 일치 무효화 가능).
  dashboardScoresAll: ['dashboard', 'scores'] as const,
  dashboardScores: (watchlistOnly: boolean, limit: number, scope: string, period: string) =>
    ['dashboard', 'scores', watchlistOnly, limit, scope, period] as const,
  marketIndices: ['market', 'indices'] as const,
  hotSectors: (limit: number) => ['market', 'sectors', limit] as const,
  marketRanking: (scope: string, sort: string, limit: number, watchlistOnly: boolean, period: string) =>
    ['market', 'ranking', scope, sort, limit, watchlistOnly, period] as const,
  indexChart: (code: string) => ['market', 'indices', code, 'chart'] as const,
  indexMinuteChart: (code: string) => ['market', 'indices', code, 'minute-chart'] as const,
  bitcoinChart: ['market', 'indices', 'bitcoin', 'minute-chart'] as const,
  bitcoinCandles: (interval: string) => ['market', 'indices', 'bitcoin', 'candles', interval] as const,
  indexMinuteCandles: (code: string) => ['market', 'indices', code, 'minute-candles'] as const,
  exchangeRateChart: ['market', 'indices', 'usdkrw', 'chart'] as const,
  investorTrading: (code: string, interval: string) =>
    ['market', 'indices', code, 'investor-trading', interval] as const,
  feedPostsAll: ['feed', 'posts'] as const,
  feedPosts: (category?: string) => ['feed', 'posts', category ?? 'all'] as const,
  feedComments: (postId: number) => ['feed', 'posts', postId, 'comments'] as const,
  // 공지 목록·상세를 접두 키 하나로 함께 무효화한다(등록·수정·삭제 뒤)
  noticesAll: ['notices'] as const,
  noticesList: ['notices', 'list'] as const,
  notice: (noticeId: number) => ['notices', 'detail', noticeId] as const,
  videoFeed: (tickerCode?: string, channelId?: number, date?: string) =>
    ['videoFeed', tickerCode ?? 'all', channelId ?? 'all', date ?? 'all'] as const,
  videoFeedDetail: (videoId: number) => ['videoFeed', 'detail', videoId] as const,
  videoFeedChannels: () => ['videoFeed', 'channels'] as const,
  telegramFeed: (tickerCode?: string, channelId?: number, date?: string) =>
    ['telegramFeed', tickerCode ?? 'all', channelId ?? 'all', date ?? 'all'] as const,
  telegramFeedDetail: (telegramPostId: number) => ['telegramFeed', 'detail', telegramPostId] as const,
  telegramFeedChannels: () => ['telegramFeed', 'channels'] as const,
  subscriptionPlans: ['subscription', 'plans'] as const,
  subscriptionMe: ['subscription', 'me'] as const,
  subscriptionPayments: ['subscription', 'payments'] as const,
  // 읽음 처리 후 페이지 무관하게 전부 무효화해야 해 접두 키를 따로 둔다
  // (dashboardScoresAll과 동일한 이유).
  notificationsAll: ['notifications'] as const,
  notificationsList: ['notifications', 'list'] as const,
  notificationsUnreadCount: ['notifications', 'unread-count'] as const,
  // 관리자 신고 관리 - 복구/삭제 뒤 글·댓글 두 목록을 접두 키로 한 번에 무효화한다
  adminModerationAll: ['admin', 'moderation'] as const,
  adminHiddenPosts: ['admin', 'moderation', 'posts'] as const,
  adminHiddenComments: ['admin', 'moderation', 'comments'] as const,
  adminVideoChannels: ['admin', 'video-channels'] as const,
  adminTelegramChannels: ['admin', 'telegram-channels'] as const,
}
