import type { TokenResponse } from '../types/auth'

// localStorage를 단일 진실 공급원으로 둔다(포트폴리오 프로젝트 스코프의
// 실용적 선택). API 응답 바디로 토큰을 주고받는 구조상 서버 쪽에
// httpOnly 쿠키 발급이 없어, memory-only access token + httpOnly
// refresh cookie 같은 더 XSS에 강한 패턴은 백엔드 변경 없이는 불가능함 -
// 그 트레이드오프를 감수하고 단순함을 택했다.
const ACCESS_TOKEN_KEY = 'ql_access'
const REFRESH_TOKEN_KEY = 'ql_refresh'

function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

function setTokens(tokens: Pick<TokenResponse, 'accessToken' | 'refreshToken'>): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
}

function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export const tokenStorage = {
  getAccessToken,
  getRefreshToken,
  setTokens,
  clearTokens,
}
