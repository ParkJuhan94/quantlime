export type OAuthProviderName = 'google' | 'kakao' | 'naver'

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessTokenExpiresIn: number
}

export interface SocialLoginRequest {
  code: string
  redirectUri: string
}

export interface ReissueRequest {
  refreshToken: string
}
