const RECENTLY_VIEWED_KEY = 'ql_recently_viewed'
const MAX_ENTRIES = 10

export interface RecentlyViewedStock {
  stockCode: string
  stockName: string
  logoUrl: string
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
