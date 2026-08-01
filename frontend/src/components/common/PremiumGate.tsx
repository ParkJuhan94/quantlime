import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { LoginModal } from '../auth/LoginModal'

interface PremiumGateProps {
  children: React.ReactNode
}

// 스코어/백테스트처럼 구독자 전용인 화면을 감싼다. children은 실제 값이
// 아니라 라벨+회색 스켈레톤 막대로 이루어진 "구조만 있는" 마크업이어야
// 한다(실데이터를 받아 CSS로 가리는 방식은 개발자도구/네트워크 탭으로
// 그대로 새어나가 실질적 차단이 아니다 - 백엔드가 애초에 이 값을 안
// 내려주고, 프론트도 구독자가 아니면 조회 자체를 안 한다).
export function PremiumGate({ children }: PremiumGateProps) {
  const { isAuthenticated } = useAuth()
  const [loginModalOpen, setLoginModalOpen] = useState(false)

  return (
    <div className="relative isolate">
      <div className="pointer-events-none select-none blur-[5px] opacity-70" aria-hidden="true">
        {children}
      </div>
      <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-center">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="1.6">
          <rect x="5" y="11" width="14" height="9" rx="2" />
          <path d="M8 11V7a4 4 0 0 1 8 0v4" />
        </svg>
        <p className="text-sm font-semibold text-gray-500">구독자 전용 기능이에요</p>
        {isAuthenticated ? (
          <Link
            to="/subscribe"
            className="rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-gray-800"
          >
            구독하고 확인하기
          </Link>
        ) : (
          <div className="relative">
            <button
              type="button"
              onClick={() => setLoginModalOpen((prev) => !prev)}
              className="rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-gray-800"
            >
              로그인하고 확인하기
            </button>
            <LoginModal open={loginModalOpen} onClose={() => setLoginModalOpen(false)} />
          </div>
        )}
      </div>
    </div>
  )
}
