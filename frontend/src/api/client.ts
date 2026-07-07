import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { env } from '../config/env'
import { tokenStorage } from '../auth/tokenStorage'
import type { TokenResponse } from '../types/auth'

type RetriableRequestConfig = InternalAxiosRequestConfig & { _retried?: boolean }

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
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
  const refreshToken = tokenStorage.getRefreshToken()
  if (!refreshToken) {
    throw new Error('저장된 리프레시 토큰이 없습니다.')
  }
  // apiClient가 아닌 axios를 직접 써서, 재발급 요청 자체가 요청
  // 인터셉터에서 만료된 액세스 토큰을 다시 붙이는 것을 피한다.
  const { data } = await axios.post<TokenResponse>(
    `${env.apiBaseUrl}/api/auth/reissue`,
    { refreshToken },
  )
  tokenStorage.setTokens(data)
  return data.accessToken
}

function redirectToLogin(): void {
  tokenStorage.clearTokens()
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
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
