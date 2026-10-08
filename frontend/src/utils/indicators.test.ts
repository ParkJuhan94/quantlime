import { describe, expect, it } from 'vitest'
import {
  calculateBollingerBands,
  calculateIchimoku,
  calculateMACD,
  calculateSMA,
  shiftBusinessDays,
  shiftTradingDate,
} from './indicators'
import {
  CLOSES,
  EXPECTED_BB_LOWER,
  EXPECTED_BB_MIDDLE,
  EXPECTED_BB_UPPER,
  EXPECTED_HISTOGRAM,
  EXPECTED_KIJUN,
  EXPECTED_MACD,
  EXPECTED_SENKOU_A,
  EXPECTED_SENKOU_B,
  EXPECTED_SIGNAL,
  EXPECTED_SMA5,
  EXPECTED_TENKAN,
  HIGHS,
  LOWS,
} from './indicators.fixture'

function expectSeriesClose(actual: (number | null)[], expected: (number | null)[]) {
  expect(actual).toHaveLength(expected.length)
  expected.forEach((value, i) => {
    if (value === null) {
      expect(actual[i], `index ${i}은 계산 불가 구간이라 null이어야 한다`).toBeNull()
    } else {
      expect(actual[i], `index ${i}`).not.toBeNull()
      expect(actual[i] as number).toBeCloseTo(value, 6)
    }
  })
}

describe('calculateSMA', () => {
  it('기간 이전 구간은 null이고 이후는 이동평균이다', () => {
    expect(calculateSMA([1, 2, 3, 4, 5], 3)).toEqual([null, null, 2, 3, 4])
  })

  it('독립 계산한 기대값과 일치한다', () => {
    expectSeriesClose(calculateSMA(CLOSES, 5), EXPECTED_SMA5)
  })

  it('데이터가 기간보다 짧으면 전부 null이다', () => {
    expect(calculateSMA([1, 2], 5)).toEqual([null, null])
  })

  it('빈 배열이면 빈 배열이다', () => {
    expect(calculateSMA([], 5)).toEqual([])
  })
})

describe('calculateBollingerBands', () => {
  it('독립 계산한 기대값(모표준편차 기준)과 일치한다', () => {
    const { upper, middle, lower } = calculateBollingerBands(CLOSES, 20, 2)

    expectSeriesClose(middle, EXPECTED_BB_MIDDLE)
    expectSeriesClose(upper, EXPECTED_BB_UPPER)
    expectSeriesClose(lower, EXPECTED_BB_LOWER)
  })

  it('종가가 모두 같으면 표준편차가 0이라 세 밴드가 같다', () => {
    const { upper, middle, lower } = calculateBollingerBands(new Array(25).fill(100), 20, 2)

    expect(upper[24]).toBe(100)
    expect(middle[24]).toBe(100)
    expect(lower[24]).toBe(100)
  })

  it('상단-중단 간격과 중단-하단 간격은 항상 같다(대칭)', () => {
    const { upper, middle, lower } = calculateBollingerBands(CLOSES, 20, 2)

    for (let i = 19; i < CLOSES.length; i++) {
      expect((upper[i] as number) - (middle[i] as number)).toBeCloseTo((middle[i] as number) - (lower[i] as number), 9)
    }
  })

  it('승수를 키우면 밴드 폭이 비례해서 넓어진다', () => {
    const narrow = calculateBollingerBands(CLOSES, 20, 1)
    const wide = calculateBollingerBands(CLOSES, 20, 3)
    const i = CLOSES.length - 1

    expect((wide.upper[i] as number) - (wide.middle[i] as number)).toBeCloseTo(
      3 * ((narrow.upper[i] as number) - (narrow.middle[i] as number)),
      9,
    )
  })
})

describe('calculateMACD', () => {
  it('독립 계산한 기대값(SMA 시드 EMA)과 일치한다', () => {
    const { macdLine, signalLine, histogram } = calculateMACD(CLOSES, 12, 26, 9)

    expectSeriesClose(macdLine, EXPECTED_MACD)
    expectSeriesClose(signalLine, EXPECTED_SIGNAL)
    expectSeriesClose(histogram, EXPECTED_HISTOGRAM)
  })

  it('MACD는 slow-1번째 인덱스부터, 시그널은 slow+signal-2번째부터 값이 생긴다', () => {
    const { macdLine, signalLine } = calculateMACD(CLOSES, 12, 26, 9)

    expect(macdLine[24]).toBeNull()
    expect(macdLine[25]).not.toBeNull()
    expect(signalLine[32]).toBeNull()
    expect(signalLine[33]).not.toBeNull()
  })

  it('종가가 일정하면 MACD/시그널/히스토그램이 모두 0이다', () => {
    const { macdLine, signalLine, histogram } = calculateMACD(new Array(50).fill(100))

    expect(macdLine[49]).toBeCloseTo(0, 12)
    expect(signalLine[49]).toBeCloseTo(0, 12)
    expect(histogram[49]).toBeCloseTo(0, 12)
  })

  it('히스토그램은 항상 MACD - 시그널이다', () => {
    const { macdLine, signalLine, histogram } = calculateMACD(CLOSES)

    histogram.forEach((value, i) => {
      if (value !== null) {
        expect(value).toBeCloseTo((macdLine[i] as number) - (signalLine[i] as number), 12)
      }
    })
  })
})

describe('calculateIchimoku', () => {
  it('독립 계산한 기대값(작은 기간 3/5/7)과 일치한다', () => {
    const result = calculateIchimoku(HIGHS, LOWS, CLOSES, 3, 5, 7)

    expectSeriesClose(result.tenkan, EXPECTED_TENKAN)
    expectSeriesClose(result.kijun, EXPECTED_KIJUN)
    expectSeriesClose(result.senkouA, EXPECTED_SENKOU_A)
    expectSeriesClose(result.senkouB, EXPECTED_SENKOU_B)
  })

  it('후행스팬은 종가 사본이며 원본 배열과 다른 참조다(시프트는 호출 측 몫)', () => {
    const result = calculateIchimoku(HIGHS, LOWS, CLOSES, 3, 5, 7)

    expect(result.chikou).toEqual(CLOSES)
    expect(result.chikou).not.toBe(CLOSES)
  })

  it('선행스팬A는 전환선과 기준선이 모두 있을 때부터 생긴다', () => {
    const result = calculateIchimoku(HIGHS, LOWS, CLOSES, 3, 5, 7)

    expect(result.senkouA[3]).toBeNull()
    expect(result.senkouA[4]).not.toBeNull()
  })
})

describe('shiftTradingDate', () => {
  // 5/5(어린이날)는 거래일 배열에 없다
  const dates = ['2026-05-01', '2026-05-04', '2026-05-06', '2026-05-07', '2026-05-08']

  it('범위 안이면 배열의 실제 거래일을 쓴다(휴장일 5/5를 거치지 않는다)', () => {
    expect(shiftTradingDate(dates, 1, 1)).toBe('2026-05-06')
    expect(shiftTradingDate(dates, 3, -2)).toBe('2026-05-04')
  })

  it('과거 쪽으로 배열을 벗어나면 null이다', () => {
    expect(shiftTradingDate(dates, 1, -2)).toBeNull()
  })

  it('미래 쪽으로 벗어나면 마지막 거래일 뒤로 주말 근사한다', () => {
    expect(shiftTradingDate(dates, 4, 1)).toBe('2026-05-11')
    expect(shiftTradingDate(dates, 3, 3)).toBe('2026-05-12')
  })
})

describe('shiftBusinessDays', () => {
  it('금요일에서 +1영업일은 다음 월요일이다(주말 건너뜀)', () => {
    expect(shiftBusinessDays('2026-09-25', 1)).toBe('2026-09-28')
  })

  it('월요일에서 -1영업일은 직전 금요일이다', () => {
    expect(shiftBusinessDays('2026-09-28', -1)).toBe('2026-09-25')
  })

  it('0이면 그대로다', () => {
    expect(shiftBusinessDays('2026-09-30', 0)).toBe('2026-09-30')
  })

  it('+5영업일은 정확히 일주일 뒤 같은 요일이다', () => {
    expect(shiftBusinessDays('2026-09-23', 5)).toBe('2026-09-30')
  })
})
