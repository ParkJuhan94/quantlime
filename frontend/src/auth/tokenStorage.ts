import type { TokenResponse } from '../types/auth'

// 액세스 토큰만 localStorage에 둔다. 리프레시 토큰은 2026-07-15 세션부터
// httpOnly 쿠키로 옮겨져 브라우저가 자동으로 관리한다(JS로 읽거나 쓸 수
// 없음) - XSS로 스크립트가 실행돼도 액세스 토큰(30분 수명)만 노출되고,
// 14일짜리 리프레시 토큰은 노출되지 않는다.
const ACCESS_TOKEN_KEY = 'ql_access'

function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

function setTokens(tokens: Pick<TokenResponse, 'accessToken'>): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
}

function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}

export const tokenStorage = {
  getAccessToken,
  setTokens,
  clearTokens,
}
