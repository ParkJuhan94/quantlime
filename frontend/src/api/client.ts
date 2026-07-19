import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { env } from '../config/env'
import { tokenStorage } from '../auth/tokenStorage'
import { logoutToast } from '../auth/logoutToast'
import type { TokenResponse } from '../types/auth'

type RetriableRequestConfig = InternalAxiosRequestConfig & { _retried?: boolean }

// withCredentials: 리프레시 토큰이 httpOnly 쿠키로 오가므로(2026-07-15
// 세션부터) 브라우저가 쿠키를 저장/전송하게 하려면 모든 요청에 필요하다.
// 백엔드 CORS 설정도 이미 allowCredentials(true) + 단일 오리진으로 이
// 조합을 지원한다(SockJS 때문에 먼저 켜져 있었음, SecurityConfig 참고).
export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  withCredentials: true,
})

apiClient.interceptors.request.use((config) => {
  const accessToken = tokenStorage.getAccessToken()
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

// 동시에 여러 요청이 401을 맞아도 재발급은 한 번만 - 진행 중인
// 재발급 Promise를 공유한다(single-flight).
let refreshPromise: Promise<string> | null = null

async function reissueAccessToken(): Promise<string> {
  // 리프레시 토큰은 httpOnly 쿠키로만 존재해 JS에서 읽을 수 없다 -
  // withCredentials로 쿠키를 실어 보내면 서버가 쿠키에서 직접 꺼내
  // 검증한다(요청 바디에 담을 필요가 없음). apiClient가 아닌 axios를
  // 직접 쓰는 이유는 그대로 유지 - 재발급 요청 자체가 요청 인터셉터에서
  // 만료된 액세스 토큰을 다시 붙이는 것을 피하기 위함.
  const { data } = await axios.post<TokenResponse>(
    `${env.apiBaseUrl}/api/auth/reissue`,
    null,
    { withCredentials: true },
  )
  tokenStorage.setTokens(data)
  return data.accessToken
}

// 로그인한 적 없는 사용자가 우연히 401을 맞은 경우(비로그인 상태에서
// 인증 필요 API 호출 등)까지 "로그아웃됐다"고 토스트를 띄우면 어색하므로,
// 실제로 지우는 토큰이 있었을 때만 토스트를 예약한다.
function redirectToLogin(): void {
  const hadSession = tokenStorage.getAccessToken() !== null
  tokenStorage.clearTokens()
  if (hadSession) {
    logoutToast.requestLogoutToast()
    window.location.assign('/')
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetriableRequestConfig | undefined

    if (error.response?.status !== 401 || !originalRequest) {
      return Promise.reject(error)
    }

    // 재발급/로그인 요청 자체의 401은 재시도하지 않는다(무한루프 방지).
    const requestUrl = originalRequest.url ?? ''
    if (requestUrl.includes('/api/auth/reissue') || requestUrl.includes('/api/auth/login/')) {
      redirectToLogin()
      return Promise.reject(error)
    }

    if (originalRequest._retried) {
      redirectToLogin()
      return Promise.reject(error)
    }
    originalRequest._retried = true

    try {
      refreshPromise ??= reissueAccessToken().finally(() => {
        refreshPromise = null
      })
      const newAccessToken = await refreshPromise
      originalRequest.headers.set('Authorization', `Bearer ${newAccessToken}`)
      return apiClient(originalRequest)
    } catch (reissueError) {
      redirectToLogin()
      return Promise.reject(reissueError)
    }
  },
)
