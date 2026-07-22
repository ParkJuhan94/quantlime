import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { buildAuthorizeUrl } from '../../config/oauth'
import { issueDevToken } from '../../api/auth'
import { useAuth } from '../../auth/useAuth'
import type { OAuthProviderName } from '../../types/auth'
import { LoginProviderButtons } from './LoginProviderButtons'

interface LoginModalProps {
  open: boolean
  onClose: () => void
}

// 헤더의 "로그인" 버튼 아래에 뜨는 드롭다운 패널(ProfileMenu와 동일한
// 앵커 패턴) - 화면 정중앙 오버레이가 아니라 버튼 바로 아래에서 뜨도록
// 부모(AppHeader)가 relative 컨테이너로 감싸고, 이 컴포넌트는 그 안에서
// absolute 포지셔닝만 담당한다.
export function LoginModal({ open, onClose }: LoginModalProps) {
  const { setTokens } = useAuth()
  const navigate = useNavigate()
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    function handleClickOutside(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    window.addEventListener('mousedown', handleClickOutside)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('mousedown', handleClickOutside)
    }
  }, [open, onClose])

  if (!open) return null

  function handleProviderLogin(provider: OAuthProviderName) {
    window.location.assign(buildAuthorizeUrl(provider))
  }

  async function handleDevLogin() {
    const tokens = await issueDevToken()
    setTokens(tokens)
    onClose()
    navigate('/', { replace: true })
  }

  return (
    <div
      ref={containerRef}
      className="absolute right-0 top-11 z-50 w-80 rounded-2xl border border-gray-100 bg-white p-6 shadow-lg"
    >
      <p className="shimmer-text mb-4 text-center text-sm font-semibold leading-relaxed">
        실시간 시세와 퀀트라임만의 스코어를
        <br />
        가장 먼저 받아보세요
      </p>

      <LoginProviderButtons onSelect={handleProviderLogin} />

      {import.meta.env.DEV && (
        <button
          type="button"
          onClick={() => void handleDevLogin()}
          className="mt-3 w-full rounded-xl bg-gray-900 py-3 text-sm font-semibold text-white transition hover:bg-gray-800"
        >
          개발용 로그인
        </button>
      )}
    </div>
  )
}
