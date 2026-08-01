const RECENTLY_VIEWED_KEY = 'ql_recently_viewed'
const MAX_ENTRIES = 10

export interface RecentlyViewedStock {
  stockCode: string
  stockName: string
  logoUrl: string
  // 해외 로고 배지 판별용(currencyForMarketType 참고) - 예전에 기록된
  // 캐시 항목엔 없을 수 있어 read()에서 optional로 취급한다.
  marketType?: string
}

function read(): RecentlyViewedStock[] {
  try {
    const raw = localStorage.getItem(RECENTLY_VIEWED_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (v): v is RecentlyViewedStock =>
        typeof v === 'object' &&
        v !== null &&
        typeof (v as RecentlyViewedStock).stockCode === 'string' &&
        typeof (v as RecentlyViewedStock).stockName === 'string',
    )
  } catch {
    return []
  }
}

function record(stock: RecentlyViewedStock): RecentlyViewedStock[] {
  const next = [stock, ...read().filter((s) => s.stockCode !== stock.stockCode)].slice(0, MAX_ENTRIES)
  localStorage.setItem(RECENTLY_VIEWED_KEY, JSON.stringify(next))
  return next
}

export const recentlyViewedStorage = {
  read,
  record,
}
