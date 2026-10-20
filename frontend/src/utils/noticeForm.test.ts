import { describe, expect, it } from 'vitest'
import { formatNoticeDate, NOTICE_LIMITS, validateNoticeForm } from './noticeForm'

describe('validateNoticeForm', () => {
  it('정상 입력이면 null이다', () => {
    expect(validateNoticeForm({ title: '점검 안내', content: '내일 02시 점검' })).toBeNull()
  })

  it('제목·내용이 공백뿐이면 거른다', () => {
    expect(validateNoticeForm({ title: '  ', content: '본문' })).toContain('제목')
    expect(validateNoticeForm({ title: '제목', content: '\n ' })).toContain('내용')
  })

  it('백엔드 한도(제목 200, 내용 5000)를 넘으면 거른다', () => {
    expect(validateNoticeForm({ title: 'a'.repeat(NOTICE_LIMITS.title), content: '본문' })).toBeNull()
    expect(validateNoticeForm({ title: 'a'.repeat(NOTICE_LIMITS.title + 1), content: '본문' })).toContain('200자')
    expect(validateNoticeForm({ title: '제목', content: 'a'.repeat(NOTICE_LIMITS.content + 1) })).toContain('5000자')
  })
})

describe('formatNoticeDate', () => {
  it('ISO 일시에서 날짜만 남긴다', () => {
    expect(formatNoticeDate('2026-10-20T09:30:00')).toBe('2026-10-20')
  })
})
