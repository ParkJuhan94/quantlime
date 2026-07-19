import { apiClient } from './client'
import type { OAuthProviderName, SocialLoginRequest, TokenResponse } from '../types/auth'

export async function login(
  provider: OAuthProviderName,
  request: SocialLoginRequest,
): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>(`/api/auth/login/${provider}`, request)
  return data
}

// 재발급은 axios 인터셉터(api/client.ts)가 리프레시 토큰 쿠키를 이용해
// 직접 처리한다 - 여기 별도 wrapper를 두지 않는다(예전엔 있었지만 실제
// 호출부가 인터셉터뿐이라 죽은 코드였음).

export async function logout(): Promise<void> {
  await apiClient.post('/api/auth/logout')
}

/** 개발 프로필 전용 - 실제 소셜 로그인 없이 테스트 유저 JWT 발급. */
export async function issueDevToken(): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/dev/auth/token')
  return data
}
