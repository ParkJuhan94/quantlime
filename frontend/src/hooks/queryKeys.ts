export const queryKeys = {
  watchlist: ['watchlist'] as const,
  stockSearch: (q: string, page: number) => ['stocks', 'search', q, page] as const,
  stockDetail: (stockCode: string) => ['stocks', 'detail', stockCode] as const,
  stockPrice: (stockCode: string) => ['stocks', 'price', stockCode] as const,
  stockChart: (stockCode: string, days: number) => ['stocks', 'chart', stockCode, days] as const,
  stockScore: (stockCode: string) => ['stocks', 'score', stockCode] as const,
  dashboardScores: ['dashboard', 'scores'] as const,
}
