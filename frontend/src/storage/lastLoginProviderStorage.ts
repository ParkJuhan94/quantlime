import type { OAuthProviderName } from '../types/auth'

// 로그인 모달에서 "최근에 이 방법으로 로그인했어요"를 보여주기 위한
// 기기(브라우저) 단위 힌트 - 계정과 무관하게 이 브라우저에서 마지막으로
// 성공한 로그인 방식만 기억한다(tokenStorage와 동일한 로컬 전용 패턴).
const KEY = 'ql_last_login_provider'

function get(): OAuthProviderName | null {
  const value = localStorage.getItem(KEY)
  return value === 'google' || value === 'kakao' || value === 'naver' ? value : null
}

function set(provider: OAuthProviderName): void {
  localStorage.setItem(KEY, provider)
}

export const lastLoginProviderStorage = {
  get,
  set,
}
