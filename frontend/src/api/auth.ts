import { apiClient } from './client'
import type {
  OAuthProviderName,
  ReissueRequest,
  SocialLoginRequest,
  TokenResponse,
} from '../types/auth'

export async function login(
  provider: OAuthProviderName,
  request: SocialLoginRequest,
): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>(`/api/auth/login/${provider}`, request)
  return data
}

export async function reissue(request: ReissueRequest): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/api/auth/reissue', request)
  return data
}

export async function logout(): Promise<void> {
  await apiClient.post('/api/auth/logout')
}

/** 개발 프로필 전용 - 실제 소셜 로그인 없이 테스트 유저 JWT 발급. */
export async function issueDevToken(): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/dev/auth/token')
  return data
}
