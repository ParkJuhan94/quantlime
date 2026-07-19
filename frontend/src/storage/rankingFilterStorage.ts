import type { Period, Scope, SortKey } from '../components/home/RankingTable'

const KEY = 'ql_ranking_filter'

interface RankingFilter {
  scope: Scope
  sortKey: SortKey
  period: Period
  watchlistOnly: boolean
}

const DEFAULT_FILTER: RankingFilter = { scope: 'all', sortKey: 'gainers', period: '실시간', watchlistOnly: false }

function read(): RankingFilter {
  const raw = localStorage.getItem(KEY)
  if (!raw) return DEFAULT_FILTER
  try {
    return { ...DEFAULT_FILTER, ...JSON.parse(raw) }
  } catch {
    return DEFAULT_FILTER
  }
}

function write(filter: RankingFilter): void {
  localStorage.setItem(KEY, JSON.stringify(filter))
}

export const rankingFilterStorage = {
  read,
  write,
}
