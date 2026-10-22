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

  it('400·409에서 본문이 안내 문자열이면 그대로 보여준다(배치 시작 거부 등)', () => {
    expect(describeAdminError({ response: { status: 409, data: '이미 실행 중인 배치가 있습니다: running=a' } })).toBe(
      '이미 실행 중인 배치가 있습니다: running=a',
    )
    expect(describeAdminError({ response: { status: 400, data: '지원하지 않는 시장입니다: TOKYO' } })).toBe(
      '지원하지 않는 시장입니다: TOKYO',
    )
    // 다른 상태 코드의 문자열 본문(예: nginx 오류 페이지)은 노출하지 않는다
    expect(describeAdminError({ response: { status: 500, data: '<html>error</html>' } })).toContain('요청에 실패했어요')
  })

  it('권한·인증 오류와 알 수 없는 오류를 구분한다', () => {
    expect(describeAdminError({ response: { status: 403 } })).toContain('관리자 권한')
    expect(describeAdminError({ response: { status: 401 } })).toContain('다시 로그인')
    expect(describeAdminError(new Error('x'))).toContain('요청에 실패했어요')
    expect(describeAdminError(undefined)).toContain('요청에 실패했어요')
  })
})
