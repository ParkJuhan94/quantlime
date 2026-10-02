import { describe, expect, it } from 'vitest'
import {
  changeRateColorClass,
  currencyForMarketType,
  formatChangeRate,
  formatKrwAmount,
  formatPrice,
  formatTradingAmount,
} from './priceFormat'

describe('currencyForMarketType', () => {
  it('나스닥/뉴욕증권거래소만 USD이고 나머지(국내, null, undefined)는 KRW다', () => {
    expect(currencyForMarketType('나스닥')).toBe('USD')
    expect(currencyForMarketType('뉴욕증권거래소')).toBe('USD')
    expect(currencyForMarketType('코스피')).toBe('KRW')
    expect(currencyForMarketType('코스닥')).toBe('KRW')
    expect(currencyForMarketType(null)).toBe('KRW')
    expect(currencyForMarketType(undefined)).toBe('KRW')
  })
})

describe('formatPrice', () => {
  it('null/undefined는 "-"다', () => {
    expect(formatPrice(null)).toBe('-')
    expect(formatPrice(undefined, 'USD')).toBe('-')
  })

  it('국내는 소수점을 반올림한 정수 + 원이다(기본 통화)', () => {
    expect(formatPrice(71_000)).toBe('71,000원')
    expect(formatPrice(71_000.6)).toBe('71,001원')
    expect(formatPrice(0)).toBe('0원')
  })

  it('해외는 항상 소수점 2자리 + $다(저가주 보존)', () => {
    expect(formatPrice(189.5, 'USD')).toBe('$189.50')
    expect(formatPrice(0.1234, 'USD')).toBe('$0.12')
    expect(formatPrice(1234.567, 'USD')).toBe('$1,234.57')
  })
})

describe('formatKrwAmount', () => {
  it('null/undefined는 "-"이고 0은 부호 없이 원 단위다', () => {
    expect(formatKrwAmount(null)).toBe('-')
    expect(formatKrwAmount(undefined)).toBe('-')
    expect(formatKrwAmount(0)).toBe('0원')
  })

  it('억 미만은 원, 억 이상은 억, 조 이상은 소수 1자리 조로 압축한다', () => {
    expect(formatKrwAmount(9_999)).toBe('+9,999원')
    expect(formatKrwAmount(150_000_000)).toBe('+2억') // 1.5억 -> 반올림
    expect(formatKrwAmount(12_345_000_000)).toBe('+123억')
    expect(formatKrwAmount(1_234_000_000_000)).toBe('+1.2조')
  })

  it('순매도(음수)는 - 부호를 붙인다', () => {
    expect(formatKrwAmount(-300_000_000)).toBe('-3억')
    expect(formatKrwAmount(-2_000_000_000_000)).toBe('-2.0조')
  })

  it('경계값: 정확히 1억은 억 단위, 정확히 1조는 조 단위다', () => {
    expect(formatKrwAmount(100_000_000)).toBe('+1억')
    expect(formatKrwAmount(1_000_000_000_000)).toBe('+1.0조')
  })
})

describe('formatTradingAmount', () => {
  it('null/undefined는 "-"다', () => {
    expect(formatTradingAmount(null)).toBe('-')
  })

  it('부호를 붙이지 않고 국내는 원, 해외는 달러 단위를 쓴다', () => {
    expect(formatTradingAmount(150_000_000)).toBe('2억원')
    expect(formatTradingAmount(1_700_000_000, 'USD')).toBe('17억달러')
    expect(formatTradingAmount(2_500_000_000_000)).toBe('2.5조원')
    expect(formatTradingAmount(5_000)).toBe('5,000원')
    expect(formatTradingAmount(5_000, 'USD')).toBe('5,000달러')
  })
})

describe('formatChangeRate', () => {
  it('null은 "-", 양수는 + 부호, 음수/0은 부호 없이(음수는 자체 -) 소수 2자리다', () => {
    expect(formatChangeRate(null)).toBe('-')
    expect(formatChangeRate(1.234)).toBe('+1.23%')
    expect(formatChangeRate(-0.5)).toBe('-0.50%')
    expect(formatChangeRate(0)).toBe('0.00%')
  })
})

describe('changeRateColorClass', () => {
  it('국내 관례: 상승은 빨강, 하락은 파랑, 보합은 회색, 값 없음은 연회색이다', () => {
    expect(changeRateColorClass(2)).toBe('text-red-600')
    expect(changeRateColorClass(-2)).toBe('text-blue-600')
    expect(changeRateColorClass(0)).toBe('text-gray-600')
    expect(changeRateColorClass(null)).toBe('text-gray-400')
    expect(changeRateColorClass(undefined)).toBe('text-gray-400')
  })
})
