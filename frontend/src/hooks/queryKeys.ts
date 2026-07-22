export const queryKeys = {
  watchlist: ['watchlist'] as const,
  watchlistGroups: ['watchlist', 'groups'] as const,
  me: ['me'] as const,
  stockSearch: (q: string, page: number) => ['stocks', 'search', q, page] as const,
  stockDetail: (stockCode: string) => ['stocks', 'detail', stockCode] as const,
  stockPrice: (stockCode: string) => ['stocks', 'price', stockCode] as const,
  stockChart: (stockCode: string, days: number) => ['stocks', 'chart', stockCode, days] as const,
  stockScore: (stockCode: string) => ['stocks', 'score', stockCode] as const,
  stockFundamentals: (stockCode: string) => ['stocks', 'fundamentals', stockCode] as const,
  popularStocks: (limit: number) => ['stocks', 'popular', limit] as const,
  // watchlistOnly/limit 조합별로 쿼리키가 갈라지는데(useDashboardScores),
  // 관심종목 등록/해제 후에는 조합과 무관하게 전부 무효화해야 해 접두
  // 키를 따로 둔다(React Query는 배열 접두사로 부분 일치 무효화 가능).
  dashboardScoresAll: ['dashboard', 'scores'] as const,
  dashboardScores: (watchlistOnly: boolean, limit: number) =>
    ['dashboard', 'scores', watchlistOnly, limit] as const,
  marketIndices: ['market', 'indices'] as const,
  marketRanking: (sort: string, limit: number, watchlistOnly: boolean) =>
    ['market', 'ranking', sort, limit, watchlistOnly] as const,
  indexChart: (code: string) => ['market', 'indices', code, 'chart'] as const,
  indexMinuteChart: (code: string) => ['market', 'indices', code, 'minute-chart'] as const,
  bitcoinChart: ['market', 'indices', 'bitcoin', 'minute-chart'] as const,
  exchangeRateChart: ['market', 'indices', 'usdkrw', 'chart'] as const,
  feedPostsAll: ['feed', 'posts'] as const,
  feedPosts: (category?: string) => ['feed', 'posts', category ?? 'all'] as const,
  feedComments: (postId: number) => ['feed', 'posts', postId, 'comments'] as const,
  subscriptionPlans: ['subscription', 'plans'] as const,
  subscriptionMe: ['subscription', 'me'] as const,
  subscriptionPayments: ['subscription', 'payments'] as const,
}
