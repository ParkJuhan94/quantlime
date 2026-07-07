import { useNavigate } from 'react-router-dom'
import { buildAuthorizeUrl } from '../config/oauth'
import { issueDevToken } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import type { OAuthProviderName } from '../types/auth'

const PROVIDERS: { id: OAuthProviderName; label: string }[] = [
  { id: 'google', label: '구글로 로그인' },
  { id: 'kakao', label: '카카오로 로그인' },
  { id: 'naver', label: '네이버로 로그인' },
]

export function LoginPage() {
  const { setTokens } = useAuth()
  const navigate = useNavigate()

  function handleProviderLogin(provider: OAuthProviderName) {
    window.location.assign(buildAuthorizeUrl(provider))
  }

  async function handleDevLogin() {
    const tokens = await issueDevToken()
    setTokens(tokens)
    navigate('/', { replace: true })
  }

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <div className="w-full max-w-sm rounded-xl border border-gray-200 bg-white p-8 shadow-sm">
        <h1 className="mb-6 text-center text-2xl font-semibold text-gray-900">QuantLab 로그인</h1>
        <div className="space-y-3">
          {PROVIDERS.map((provider) => (
            <button
              key={provider.id}
              type="button"
              onClick={() => handleProviderLogin(provider.id)}
              className="w-full rounded-lg border border-gray-300 py-2.5 font-medium text-gray-700 transition hover:bg-gray-50"
            >
              {provider.label}
            </button>
          ))}
        </div>
        {import.meta.env.DEV && (
          <button
            type="button"
            onClick={() => void handleDevLogin()}
            className="mt-6 w-full rounded-lg bg-gray-900 py-2.5 font-medium text-white transition hover:bg-gray-800"
          >
            개발용 로그인
          </button>
        )}
      </div>
    </div>
  )
}
