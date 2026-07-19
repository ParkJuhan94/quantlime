export type OAuthProviderName = 'google' | 'kakao' | 'naver'

// 리프레시 토큰은 응답 바디에 없다 - httpOnly 쿠키로만 내려온다
// (백엔드 RefreshTokenCookieProvider 참고).
export interface TokenResponse {
  accessToken: string
  tokenType: string
  accessTokenExpiresIn: number
}

export interface SocialLoginRequest {
  code: string
  redirectUri: string
}
