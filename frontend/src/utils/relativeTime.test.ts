import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { formatRelativeTime } from './relativeTime'

describe('formatRelativeTime', () => {
  const now = new Date('2026-09-30T12:00:00Z')

  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(now)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const ago = (ms: number) => new Date(now.getTime() - ms).toISOString()

  it('1분 미만은 "방금"이다', () => {
    expect(formatRelativeTime(ago(0))).toBe('방금')
    expect(formatRelativeTime(ago(59_999))).toBe('방금')
  })

  it('1시간 미만은 분 단위다(경계: 정확히 1분, 59분)', () => {
    expect(formatRelativeTime(ago(60_000))).toBe('1분')
    expect(formatRelativeTime(ago(59 * 60_000))).toBe('59분')
  })

  it('24시간 미만은 시간 단위다(경계: 정확히 1시간, 23시간)', () => {
    expect(formatRelativeTime(ago(60 * 60_000))).toBe('1시간')
    expect(formatRelativeTime(ago(23 * 3_600_000))).toBe('23시간')
  })

  it('24시간 이상은 일 단위다(경계: 정확히 24시간은 1일)', () => {
    expect(formatRelativeTime(ago(24 * 3_600_000))).toBe('1일')
    expect(formatRelativeTime(ago(5 * 86_400_000))).toBe('5일')
  })
})
