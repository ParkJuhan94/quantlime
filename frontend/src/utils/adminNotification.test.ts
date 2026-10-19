import { describe, expect, it } from 'vitest'
import { NOTIFICATION_LIMITS, validateNotificationForm } from './adminNotification'

const valid = { title: '점검 안내', content: '내일 02시 점검', linkUrl: '' }

describe('validateNotificationForm', () => {
  it('정상 입력이면 null이다', () => {
    expect(validateNotificationForm(valid)).toBeNull()
    expect(validateNotificationForm({ ...valid, linkUrl: '/feed' })).toBeNull()
  })

  it('제목·내용이 공백뿐이면 거른다', () => {
    expect(validateNotificationForm({ ...valid, title: '   ' })).toContain('제목')
    expect(validateNotificationForm({ ...valid, content: '' })).toContain('내용')
  })

  it('백엔드 컬럼 한도(제목 100, 내용 500)를 넘으면 거른다', () => {
    expect(validateNotificationForm({ ...valid, title: 'a'.repeat(NOTIFICATION_LIMITS.title) })).toBeNull()
    expect(validateNotificationForm({ ...valid, title: 'a'.repeat(NOTIFICATION_LIMITS.title + 1) })).toContain('100자')
    expect(validateNotificationForm({ ...valid, content: 'a'.repeat(NOTIFICATION_LIMITS.content + 1) })).toContain(
      '500자',
    )
  })

  it('링크는 비워 두거나 사이트 내부 경로만 허용한다', () => {
    expect(validateNotificationForm({ ...valid, linkUrl: '   ' })).toBeNull()
    expect(validateNotificationForm({ ...valid, linkUrl: 'https://example.com' })).toContain('사이트 안')
    expect(validateNotificationForm({ ...valid, linkUrl: '//evil.example' })).toContain('사이트 안')
    expect(validateNotificationForm({ ...valid, linkUrl: 'feed' })).toContain('사이트 안')
  })
})
