import type { Period, Scope, SortKey } from '../components/home/RankingTable'

const KEY = 'ql_ranking_filter'

interface RankingFilter {
  scope: Scope
  sortKey: SortKey
  period: Period
  watchlistOnly: boolean
}

const DEFAULT_FILTER: RankingFilter = { scope: 'domestic', sortKey: 'gainers', period: '실시간', watchlistOnly: false }

function read(): RankingFilter {
  const raw = localStorage.getItem(KEY)
  if (!raw) return DEFAULT_FILTER
  try {
    const stored = { ...DEFAULT_FILTER, ...JSON.parse(raw) }
    // '전체' 탭을 없애기 전에 저장된 값은 국내로 읽는다(그 탭도 급상승 등은 국내 데이터를 보여줬다).
    return stored.scope === 'overseas' ? stored : { ...stored, scope: 'domestic' }
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
