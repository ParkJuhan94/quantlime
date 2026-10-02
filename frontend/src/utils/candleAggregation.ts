import type { DailyChartResponse } from '../types/stock'

export type ChartInterval = 'daily' | 'weekly' | 'monthly'

// 백엔드는 일봉만 내려준다(§6, period=daily만 지원) - 주봉/월봉은 별도
// 수집 파이프라인 없이 이미 받아온 일봉을 프론트에서 그대로 묶어서
// 만든다. 분봉은 이 방식으로 만들 수 없다(애초에 일 단위보다 촘촘한
// 원본 데이터 자체가 없음 - 별도 수집 인프라가 필요해 이번 범위에서 제외).
function weekKey(dateStr: string): string {
  const date = new Date(`${dateStr}T00:00:00`)
  // ISO 주(월요일 시작) 기준으로 묶는다.
  const day = (date.getDay() + 6) % 7
  const monday = new Date(date)
  monday.setDate(date.getDate() - day)
  // toISOString()은 UTC 기준이라 로컬 자정 Date를 UTC+ 타임존(KST 등)에서 변환하면 키가 하루 전
  // 날짜(일요일)로 나온다 - 그룹핑 결과는 모든 날짜가 같은 만큼 밀려 달라지지 않았지만, 키가 "그 주의
  // 월요일"이라는 의미를 갖도록 로컬 연/월/일을 그대로 쓴다(utils/indicators.ts shiftBusinessDays와 같은 이유).
  const month = String(monday.getMonth() + 1).padStart(2, '0')
  const dayOfMonth = String(monday.getDate()).padStart(2, '0')
  return `${monday.getFullYear()}-${month}-${dayOfMonth}`
}

function monthKey(dateStr: string): string {
  return dateStr.slice(0, 7)
}

function aggregateGroup(group: DailyChartResponse[]): DailyChartResponse {
  const first = group[0]
  const last = group[group.length - 1]
  return {
    tradeDate: last.tradeDate,
    open: first.open,
    high: Math.max(...group.map((bar) => bar.high)),
    low: Math.min(...group.map((bar) => bar.low)),
    close: last.close,
    volume: group.reduce((sum, bar) => sum + bar.volume, 0),
  }
}

export function aggregateCandles(data: DailyChartResponse[], interval: ChartInterval): DailyChartResponse[] {
  if (interval === 'daily' || data.length === 0) return data

  const keyFn = interval === 'weekly' ? weekKey : monthKey
  const groups: DailyChartResponse[][] = []
  let currentKey: string | null = null

  for (const bar of data) {
    const key = keyFn(bar.tradeDate)
    if (key !== currentKey) {
      groups.push([bar])
      currentKey = key
    } else {
      groups[groups.length - 1].push(bar)
    }
  }

  return groups.map(aggregateGroup)
}
