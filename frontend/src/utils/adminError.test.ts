import { describe, expect, it } from 'vitest'
import { describeAdminError } from './adminError'

describe('describeAdminError', () => {
  it('게이트웨이/타임아웃 계열은 "서버에서 계속 처리 중일 수 있다"고 안내한다', () => {
    for (const status of [502, 503, 504]) {
      expect(describeAdminError({ response: { status } })).toContain('계속 처리 중')
    }
    expect(describeAdminError({ code: 'ECONNABORTED' })).toContain('계속 처리 중')
  })

  it('네트워크 오류는 연결 실패로 안내한다', () => {
    expect(describeAdminError({ code: 'ERR_NETWORK' })).toContain('연결하지 못했어요')
  })

  it('백엔드가 준 message를 우선 보여준다', () => {
    expect(describeAdminError({ response: { status: 400, data: { message: '이미 실행 중입니다.' } } })).toBe(
      '이미 실행 중입니다.',
    )
  })

  it('권한·인증 오류와 알 수 없는 오류를 구분한다', () => {
    expect(describeAdminError({ response: { status: 403 } })).toContain('관리자 권한')
    expect(describeAdminError({ response: { status: 401 } })).toContain('다시 로그인')
    expect(describeAdminError(new Error('x'))).toContain('요청에 실패했어요')
    expect(describeAdminError(undefined)).toContain('요청에 실패했어요')
  })
})
