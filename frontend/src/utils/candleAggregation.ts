import type { DailyChartResponse, MinuteCandle } from '../types/stock'

export type ChartInterval = 'daily' | 'weekly' | 'monthly'

// 분봉은 서버가 1분봉만 내려주므로(토스 API 제약) N분봉은 프론트에서 묶는다. 일/주/월봉과 같은
// 선택 상태로 다루려고 'minute:5' 같은 문자열 하나로 합친다.
export const MINUTE_UNITS = [1, 3, 5, 15, 30, 60] as const
export type MinuteUnit = (typeof MINUTE_UNITS)[number]
export type MinuteInterval = `minute:${MinuteUnit}`

export function isMinuteInterval(interval: ChartInterval | MinuteInterval): interval is MinuteInterval {
  return interval.startsWith('minute:')
}

export function minuteUnitOf(interval: string): MinuteUnit | null {
  const unit = MINUTE_UNITS.find((candidate) => interval === `minute:${candidate}`)
  return unit ?? null
}

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

// 페이지를 이어 붙이면 경계에서 같은 시각이 겹칠 수 있어 time 기준으로 중복을 제거하고 오름차순으로 정렬한다.
export function normalizeMinuteCandles(candles: MinuteCandle[]): MinuteCandle[] {
  const byTime = new Map<number, MinuteCandle>()
  candles.forEach((candle) => byTime.set(candle.time, candle))
  return [...byTime.values()].sort((a, b) => a.time - b.time)
}

// 1분봉(오름차순, 중복 없음)을 N분봉으로 묶는다. 버킷은 epoch 초를 N분 단위로 내림해 만든다 - 한국(UTC+9)과
// 미국(UTC-4/-5)의 시차가 모두 정시 단위라 60분 이하 단위의 경계는 현지 시각과 어긋나지 않는다.
// 시가=첫 봉, 종가=마지막 봉, 고저=구간 극값, 거래량=합. 가장 오래된 버킷은 아직 이어받지 못한 앞부분 때문에
// 덜 찬 상태일 수 있다(다음 페이지를 받으면 채워진다).
export function aggregateMinuteCandles(candles: MinuteCandle[], unit: MinuteUnit): MinuteCandle[] {
  if (unit === 1 || candles.length === 0) return candles

  const bucketSeconds = unit * 60
  const result: MinuteCandle[] = []
  for (const candle of candles) {
    const bucketTime = Math.floor(candle.time / bucketSeconds) * bucketSeconds
    const current = result[result.length - 1]
    if (current && current.time === bucketTime) {
      current.high = Math.max(current.high, candle.high)
      current.low = Math.min(current.low, candle.low)
      current.close = candle.close
      current.volume += candle.volume
    } else {
      result.push({ ...candle, time: bucketTime })
    }
  }
  return result
}

// 일봉 페이지(최근 구간 → 더 과거 구간 순)를 하나로 합친다. 경계 날짜가 겹쳐도 하나만 남기고 오름차순으로 정렬한다.
export function mergeDailyPages(pages: DailyChartResponse[][]): DailyChartResponse[] {
  const byDate = new Map<string, DailyChartResponse>()
  pages.forEach((page) => page.forEach((bar) => byDate.set(bar.tradeDate, bar)))
  return [...byDate.values()].sort((a, b) => a.tradeDate.localeCompare(b.tradeDate))
}

// 다음(더 과거) 일봉 페이지의 종료일 - 이번 페이지에서 가장 오래된 날짜의 전날. 페이지가 비어 있으면 끝이다.
export function nextDailyPageEnd(page: DailyChartResponse[]): string | undefined {
  if (page.length === 0) return undefined
  const oldest = page.reduce((min, bar) => (bar.tradeDate < min ? bar.tradeDate : min), page[0].tradeDate)
  const date = new Date(`${oldest}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() - 1)
  return date.toISOString().slice(0, 10)
}
