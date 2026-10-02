import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  VIDEO_FEED_RETENTION_DAYS,
  clampToRetentionWindow,
  daysAgo,
  formatDayLabel,
  formatUpdatedTimeLabel,
  formatVideoPublishedAt,
  shiftDateString,
  todayDateString,
} from './dateFilter'

// 로컬 타임존 생성자로 "2026-09-30 15:00(로컬)"을 고정한다 - 어떤 타임존의 머신/CI에서도 같은 결과여야 한다
const NOW = new Date(2026, 8, 30, 15, 0, 0)

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(NOW)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('todayDateString / shiftDateString', () => {
  it('오늘 날짜를 로컬 기준 yyyy-MM-dd로 만든다', () => {
    expect(todayDateString()).toBe('2026-09-30')
  })

  it('일 단위로 이동하며 월/연 경계를 넘는다', () => {
    expect(shiftDateString('2026-09-30', 1)).toBe('2026-10-01')
    expect(shiftDateString('2026-10-01', -1)).toBe('2026-09-30')
    expect(shiftDateString('2026-12-31', 1)).toBe('2027-01-01')
    expect(shiftDateString('2026-03-01', -1)).toBe('2026-02-28')
    expect(shiftDateString('2028-03-01', -1)).toBe('2028-02-29') // 윤년
    expect(shiftDateString('2026-09-30', 0)).toBe('2026-09-30')
  })
})

describe('clampToRetentionWindow', () => {
  it('보존 기간(14일) 안의 날짜는 그대로다', () => {
    expect(clampToRetentionWindow('2026-09-30')).toBe('2026-09-30')
    expect(clampToRetentionWindow('2026-09-20')).toBe('2026-09-20')
  })

  it('가장 오래된 날짜 경계(오늘-14일)는 그대로, 그보다 과거는 경계로 당긴다', () => {
    const oldest = shiftDateString('2026-09-30', -VIDEO_FEED_RETENTION_DAYS)
    expect(clampToRetentionWindow(oldest)).toBe(oldest)
    expect(clampToRetentionWindow('2020-01-01')).toBe(oldest)
  })

  it('미래 날짜는 오늘로 당긴다', () => {
    expect(clampToRetentionWindow('2026-10-05')).toBe('2026-09-30')
  })
})

describe('daysAgo', () => {
  it('자정 기준 날짜 차이다(시각과 무관)', () => {
    expect(daysAgo('2026-09-30')).toBe(0)
    expect(daysAgo('2026-09-29')).toBe(1)
    expect(daysAgo('2026-09-16')).toBe(14)
  })

  it('월 경계를 넘어서도 정확하다', () => {
    expect(daysAgo('2026-08-31')).toBe(30)
  })
})

describe('formatDayLabel', () => {
  it('오늘이면 "오늘 · M월 D일(요일)"이다', () => {
    expect(formatDayLabel('2026-09-30')).toBe('오늘 · 9월 30일(수)')
  })

  it('과거는 "M월 D일(요일) · N일 전"이다', () => {
    expect(formatDayLabel('2026-09-28')).toBe('9월 28일(월) · 2일 전')
  })
})

describe('formatUpdatedTimeLabel', () => {
  it('로컬 시각을 HH:mm 기준으로 0 패딩해 표시한다', () => {
    expect(formatUpdatedTimeLabel(new Date(2026, 8, 30, 8, 5).toISOString())).toBe('08:05 기준')
    expect(formatUpdatedTimeLabel(new Date(2026, 8, 30, 20, 30).toISOString())).toBe('20:30 기준')
  })
})

describe('formatVideoPublishedAt', () => {
  const at = (date: Date) => date.toISOString()

  it('24시간 미만은 방금/분/시간이다', () => {
    expect(formatVideoPublishedAt(at(NOW))).toBe('방금')
    expect(formatVideoPublishedAt(at(new Date(NOW.getTime() - 30 * 60_000)))).toBe('30분')
    expect(formatVideoPublishedAt(at(new Date(NOW.getTime() - 5 * 3_600_000)))).toBe('5시간')
  })

  it('24시간 이상은 자정 기준 날짜 차이다 - 필터의 "N일 전" 라벨과 같은 값이어야 한다', () => {
    // 어제 23:00 게시, 지금 오늘 15:00 = 16시간 경과지만 24시간 미만이라 "16시간"
    expect(formatVideoPublishedAt(at(new Date(2026, 8, 29, 23, 0)))).toBe('16시간')
    // 이틀 전 10:00 게시 = 53시간 경과, 자정 기준 2일 전
    expect(formatVideoPublishedAt(at(new Date(2026, 8, 28, 10, 0)))).toBe('2일')
    expect(formatVideoPublishedAt(at(new Date(2026, 8, 28, 10, 0)))).toBe(`${daysAgo('2026-09-28')}일`)
  })
})
