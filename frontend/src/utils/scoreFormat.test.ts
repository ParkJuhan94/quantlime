import { describe, expect, it } from 'vitest'
import { formatScore, formatScoreDate } from './scoreFormat'

describe('formatScore', () => {
  it('소수 1자리로 반올림하고 null/undefined는 "-"다', () => {
    expect(formatScore(43.91018446890717)).toBe('43.9')
    expect(formatScore(70)).toBe('70.0')
    expect(formatScore(0)).toBe('0.0')
    expect(formatScore(null)).toBe('-')
    expect(formatScore(undefined)).toBe('-')
  })
})

describe('formatScoreDate', () => {
  it('yyyy-MM-dd를 "M월 D일"로 바꾸고 앞자리 0은 제거한다(타임존 영향 없이 문자열 분해)', () => {
    expect(formatScoreDate('2026-09-05')).toBe('9월 5일')
    expect(formatScoreDate('2026-12-31')).toBe('12월 31일')
  })

  it('빈 값은 "-"다', () => {
    expect(formatScoreDate(null)).toBe('-')
    expect(formatScoreDate(undefined)).toBe('-')
    expect(formatScoreDate('')).toBe('-')
  })
})
