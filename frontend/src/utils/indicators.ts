/**
 * 차트 보조지표 계산 유틸. 스코어링(quant-engine)과는 무관한, 원값 그대로의
 * 표준 공식만 구현한다 - 가중치/등급 산출 없이 캔들 위에 그대로 겹쳐 그리는 용도.
 */

export type LineDashStyle = 'solid' | 'dashed' | 'dotted'

// 라인 하나(예: MA5, BB상단, 전환선)의 표시 여부·색상·굵기·선 종류. 굵기는
// lightweight-charts의 LineWidth 타입(1~4)에 맞춘다 - 실제 차트에
// 넘길 때 클램프한다(components/chart/CandleChart.tsx의 toLineWidth 참고).
export interface LineStyleSettings {
  visible: boolean
  color: string
  width: number
  lineStyle: LineDashStyle
}

export type PriceSource = 'close' | 'open' | 'high' | 'low'

export interface MaLineSettings extends LineStyleSettings {
  period: number
  priceSource: PriceSource
}

export interface BollingerBandsParams {
  period: number
  multiplier: number
}

export interface BollingerBandsLines {
  upper: LineStyleSettings
  middle: LineStyleSettings
  lower: LineStyleSettings
}

export interface IchimokuParams {
  tenkanPeriod: number
  kijunPeriod: number
  senkouBPeriod: number
}

export interface IchimokuLines {
  tenkan: LineStyleSettings
  kijun: LineStyleSettings
  senkouA: LineStyleSettings
  senkouB: LineStyleSettings
  chikou: LineStyleSettings
}

export interface MacdParams {
  fastPeriod: number
  slowPeriod: number
  signalPeriod: number
}

export interface MacdLines {
  macd: LineStyleSettings
  signal: LineStyleSettings
}

export interface IndicatorSettings {
  volume: boolean
  ma: boolean
  maLines: MaLineSettings[]
  bollingerBands: boolean
  bollingerBandsParams: BollingerBandsParams
  bollingerBandsLines: BollingerBandsLines
  // 상단-하단 밴드 사이를 옅게 채울지 여부(설정 옵션, 2026-07-16).
  bollingerBandsFill: boolean
  ichimoku: boolean
  ichimokuParams: IchimokuParams
  ichimokuLines: IchimokuLines
  // 선행스팬A-B 사이(구름대)를 채울지 여부. 일목균형표는 원래 구름대
  // 채우기가 지표 정체성에 가까워 기본값을 true로 둔다.
  ichimokuCloudFill: boolean
  macd: boolean
  macdParams: MacdParams
  macdLines: MacdLines
}

function line(color: string, visible = true, width = 1, lineStyle: LineDashStyle = 'solid'): LineStyleSettings {
  return { visible, color, width, lineStyle }
}

export const DEFAULT_INDICATOR_SETTINGS: IndicatorSettings = {
  volume: true,
  ma: true,
  maLines: [
    { ...line('#f59e0b'), period: 5, priceSource: 'close' },
    { ...line('#10b981'), period: 10, priceSource: 'close' },
    { ...line('#3b82f6'), period: 20, priceSource: 'close' },
    { ...line('#8b5cf6'), period: 60, priceSource: 'close' },
    { ...line('#ec4899'), period: 120, priceSource: 'close' },
  ],
  bollingerBands: true,
  bollingerBandsParams: { period: 20, multiplier: 2 },
  bollingerBandsLines: {
    upper: line('#94a3b8', true, 1, 'dashed'),
    middle: line('#64748b', true, 1, 'dashed'),
    lower: line('#94a3b8', true, 1, 'dashed'),
  },
  bollingerBandsFill: false,
  ichimoku: true,
  ichimokuParams: { tenkanPeriod: 9, kijunPeriod: 26, senkouBPeriod: 52 },
  ichimokuLines: {
    tenkan: line('#ef4444'),
    kijun: line('#0ea5e9'),
    senkouA: line('#22c55e', true, 1, 'dashed'),
    senkouB: line('#f97316', true, 1, 'dashed'),
    chikou: line('#a855f7', true, 1, 'dashed'),
  },
  ichimokuCloudFill: true,
  macd: true,
  macdParams: { fastPeriod: 12, slowPeriod: 26, signalPeriod: 9 },
  macdLines: {
    macd: line('#2563eb'),
    signal: line('#f97316'),
  },
}

function sma(values: number[], period: number): (number | null)[] {
  const result: (number | null)[] = new Array(values.length).fill(null)
  let sum = 0
  for (let i = 0; i < values.length; i++) {
    sum += values[i]
    if (i >= period) {
      sum -= values[i - period]
    }
    if (i >= period - 1) {
      result[i] = sum / period
    }
  }
  return result
}

/** EMA 시드값은 관례대로 첫 `period`개의 SMA를 사용한다. */
function ema(values: number[], period: number): (number | null)[] {
  const result: (number | null)[] = new Array(values.length).fill(null)
  const k = 2 / (period + 1)
  let seedSum = 0
  let prev: number | null = null

  for (let i = 0; i < values.length; i++) {
    if (i < period - 1) {
      seedSum += values[i]
      continue
    }
    if (i === period - 1) {
      seedSum += values[i]
      prev = seedSum / period
      result[i] = prev
      continue
    }
    prev = values[i] * k + (prev as number) * (1 - k)
    result[i] = prev
  }
  return result
}

export function calculateSMA(closes: number[], period: number): (number | null)[] {
  return sma(closes, period)
}

export function calculateBollingerBands(closes: number[], period = 20, multiplier = 2) {
  const middle = sma(closes, period)
  const upper: (number | null)[] = new Array(closes.length).fill(null)
  const lower: (number | null)[] = new Array(closes.length).fill(null)

  for (let i = period - 1; i < closes.length; i++) {
    const mean = middle[i] as number
    let sumSquaredDiff = 0
    for (let j = i - period + 1; j <= i; j++) {
      sumSquaredDiff += (closes[j] - mean) ** 2
    }
    const stdDev = Math.sqrt(sumSquaredDiff / period)
    upper[i] = mean + multiplier * stdDev
    lower[i] = mean - multiplier * stdDev
  }

  return { upper, middle, lower }
}

export function calculateMACD(closes: number[], fastPeriod = 12, slowPeriod = 26, signalPeriod = 9) {
  const fastEma = ema(closes, fastPeriod)
  const slowEma = ema(closes, slowPeriod)
  const macdLine: (number | null)[] = closes.map((_, i) => {
    const fast = fastEma[i]
    const slow = slowEma[i]
    return fast != null && slow != null ? fast - slow : null
  })

  // 시그널선은 MACD가 유효한 구간만 뽑아 그 부분수열에 대해 다시 EMA를 구한다.
  const validIndices: number[] = []
  const validValues: number[] = []
  macdLine.forEach((value, i) => {
    if (value != null) {
      validIndices.push(i)
      validValues.push(value)
    }
  })
  const signalOfValid = ema(validValues, signalPeriod)
  const signalLine: (number | null)[] = new Array(closes.length).fill(null)
  signalOfValid.forEach((value, idx) => {
    if (value != null) {
      signalLine[validIndices[idx]] = value
    }
  })

  const histogram: (number | null)[] = closes.map((_, i) => {
    const macd = macdLine[i]
    const signal = signalLine[i]
    return macd != null && signal != null ? macd - signal : null
  })

  return { macdLine, signalLine, histogram }
}

function highLowMidpoint(highs: number[], lows: number[], period: number, index: number): number {
  let highest = -Infinity
  let lowest = Infinity
  for (let j = index - period + 1; j <= index; j++) {
    highest = Math.max(highest, highs[j])
    lowest = Math.min(lowest, lows[j])
  }
  return (highest + lowest) / 2
}

/**
 * 일목균형표. 선행스팬A/B는 `displacement`만큼 미래로, 후행스팬은 과거로
 * 이동해서 그려야 하는데, 그 시프트(날짜 매핑)는 호출 측(차트 렌더링
 * 코드)에서 실제 날짜 문자열을 다루며 처리한다 - 여기서는 순수 값 배열만
 * 원본 인덱스 기준으로 반환한다.
 */
export function calculateIchimoku(
  highs: number[],
  lows: number[],
  closes: number[],
  tenkanPeriod = 9,
  kijunPeriod = 26,
  senkouBPeriod = 52,
) {
  const n = closes.length
  const tenkan: (number | null)[] = new Array(n).fill(null)
  const kijun: (number | null)[] = new Array(n).fill(null)
  const senkouA: (number | null)[] = new Array(n).fill(null)
  const senkouB: (number | null)[] = new Array(n).fill(null)

  for (let i = 0; i < n; i++) {
    if (i >= tenkanPeriod - 1) {
      tenkan[i] = highLowMidpoint(highs, lows, tenkanPeriod, i)
    }
    if (i >= kijunPeriod - 1) {
      kijun[i] = highLowMidpoint(highs, lows, kijunPeriod, i)
    }
    if (tenkan[i] != null && kijun[i] != null) {
      senkouA[i] = ((tenkan[i] as number) + (kijun[i] as number)) / 2
    }
    if (i >= senkouBPeriod - 1) {
      senkouB[i] = highLowMidpoint(highs, lows, senkouBPeriod, i)
    }
  }

  return { tenkan, kijun, senkouA, senkouB, chikou: closes.slice() }
}

/** 주말만 건너뛴 근사치 - 공휴일은 반영하지 않는다(차트 오버레이용
 * 표시 목적이라 스코어링만큼의 정밀도는 필요 없다는 트레이드오프). */
export function shiftBusinessDays(dateStr: string, days: number): string {
  const date = new Date(`${dateStr}T00:00:00`)
  const step = days >= 0 ? 1 : -1
  let remaining = Math.abs(days)
  while (remaining > 0) {
    date.setDate(date.getDate() + step)
    const day = date.getDay()
    if (day !== 0 && day !== 6) {
      remaining -= 1
    }
  }
  return date.toISOString().slice(0, 10)
}
