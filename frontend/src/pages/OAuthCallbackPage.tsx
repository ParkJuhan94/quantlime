import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { linkSocialAccount, login } from '../api/auth'
import { consumeMode, consumeState, getRedirectUri } from '../config/oauth'
import { useAuth } from '../auth/useAuth'
import { getErrorMessage } from '../api/errors'
import { lastLoginProviderStorage } from '../storage/lastLoginProviderStorage'
import type { OAuthProviderName } from '../types/auth'

export function OAuthCallbackPage() {
  const { provider } = useParams<{ provider: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { setTokens } = useAuth()
  const [error, setError] = useState<string | null>(null)
  // React StrictMode는 개발 모드에서 effect를 두 번 실행한다 - OAuth
  // code는 1회용이라 두 번째 교환 시도는 실패하므로, ref로 중복 실행을 막는다.
  const exchangedRef = useRef(false)

  useEffect(() => {
    if (exchangedRef.current) {
      return
    }
    exchangedRef.current = true

    async function exchangeCode(oauthProvider: OAuthProviderName) {
      const code = searchParams.get('code')
      const returnedState = searchParams.get('state')
      const expectedState = consumeState(oauthProvider)
      const mode = consumeMode()

      if (!code) {
        setError('인증 코드가 없습니다.')
        return
      }
      if (!expectedState || returnedState !== expectedState) {
        setError('요청 상태가 일치하지 않습니다. 다시 로그인해 주세요.')
        return
      }

      if (mode === 'link') {
        // 계정 연결 - 이미 로그인된 상태에서만 의미가 있고(백엔드가 401로 막는다) 토큰은 새로 받지 않는다.
        try {
          await linkSocialAccount(oauthProvider, { code, redirectUri: getRedirectUri(oauthProvider) })
          navigate('/me', { replace: true })
        } catch (linkError) {
          setError(getErrorMessage(linkError, '계정 연결에 실패했습니다.'))
        }
        return
      }

      try {
        const tokens = await login(oauthProvider, {
          code,
          redirectUri: getRedirectUri(oauthProvider),
        })
        setTokens(tokens)
        lastLoginProviderStorage.set(oauthProvider)
        navigate('/', { replace: true })
      } catch (loginError) {
        setError(getErrorMessage(loginError, '로그인에 실패했습니다.'))
      }
    }

    if (provider === 'google' || provider === 'kakao' || provider === 'naver') {
      void exchangeCode(provider)
    } else {
      setError('알 수 없는 로그인 제공자입니다.')
    }
  }, [provider, searchParams, navigate, setTokens])

  if (error) {
    return (
      <div className="flex min-h-[70vh] flex-col items-center justify-center gap-4">
        <p className="text-red-600">{error}</p>
        <a href="/" className="text-blue-600 underline">
          홈으로 돌아가기
        </a>
      </div>
    )
  }

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <p className="text-gray-500">로그인 처리 중...</p>
    </div>
  )
}
