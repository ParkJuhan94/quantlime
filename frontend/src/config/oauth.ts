import type { OAuthProviderName } from '../types/auth'

// 백엔드엔 authorize 엔드포인트가 없다 - 프론트엔드가 각 프로바이더의
// authorize URL을 직접 만들어 브라우저를 리다이렉트하고, 콜백에서 받은
// code만 백엔드로 넘긴다(백엔드가 시크릿으로 서버사이드 토큰 교환 수행).
const CLIENT_IDS: Record<OAuthProviderName, string> = {
  google: import.meta.env.VITE_GOOGLE_CLIENT_ID,
  kakao: import.meta.env.VITE_KAKAO_CLIENT_ID,
  naver: import.meta.env.VITE_NAVER_CLIENT_ID,
}

const AUTHORIZE_URLS: Record<OAuthProviderName, string> = {
  google: 'https://accounts.google.com/o/oauth2/v2/auth',
  kakao: 'https://kauth.kakao.com/oauth/authorize',
  naver: 'https://nid.naver.com/oauth2.0/authorize',
}

const SCOPES: Partial<Record<OAuthProviderName, string>> = {
  google: 'openid email profile',
}

const STATE_STORAGE_KEY_PREFIX = 'ql_oauth_state_'

/** 백엔드 .env의 GOOGLE_REDIRECT_URI 등과 정확히 일치해야 한다. */
export function getRedirectUri(provider: OAuthProviderName): string {
  return `${window.location.origin}/oauth/callback/${provider}`
}

/**
 * CSRF 방지용 state를 생성해 브라우저 리다이렉트 전에 sessionStorage에
 * 저장하고, authorize URL을 만들어 반환한다.
 */
export function buildAuthorizeUrl(provider: OAuthProviderName): string {
  const state = crypto.randomUUID()
  sessionStorage.setItem(`${STATE_STORAGE_KEY_PREFIX}${provider}`, state)

  const params = new URLSearchParams({
    client_id: CLIENT_IDS[provider],
    redirect_uri: getRedirectUri(provider),
    response_type: 'code',
    state,
  })
  const scope = SCOPES[provider]
  if (scope) {
    params.set('scope', scope)
  }

  return `${AUTHORIZE_URLS[provider]}?${params.toString()}`
}

/** 콜백에서 저장해둔 state를 꺼내며 동시에 지운다(1회성 사용). */
export function consumeState(provider: OAuthProviderName): string | null {
  const key = `${STATE_STORAGE_KEY_PREFIX}${provider}`
  const state = sessionStorage.getItem(key)
  sessionStorage.removeItem(key)
  return state
}
