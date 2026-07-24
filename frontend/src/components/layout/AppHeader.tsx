import { useEffect, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logout as logoutRequest } from '../../api/auth'
import { SearchOverlay } from '../search/SearchOverlay'
import { ProfileMenu } from './ProfileMenu'
import { LoginModal } from '../auth/LoginModal'

const navLinkClassName = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-2.5 py-1.5 text-sm transition hover:bg-gray-100 ${
    isActive ? 'font-semibold text-gray-900' : 'font-medium text-gray-600'
  }`

interface AppHeaderProps {
  onLoggedOut: () => void
}

export function AppHeader({ onLoggedOut }: AppHeaderProps) {
  const { isAuthenticated, clearAuth } = useAuth()
  const navigate = useNavigate()
  const [searchOpen, setSearchOpen] = useState(false)
  const [loginModalOpen, setLoginModalOpen] = useState(false)

  // 검색창 클릭 없이도 "/" 키 한 번으로 열 수 있게 한다 - 다른 입력 요소에
  // 포커스가 있을 땐(예: 텍스트 입력 중 "/") 가로채지 않는다.
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      const isTyping = target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable
      if (event.key === '/' && !isTyping) {
        event.preventDefault()
        setSearchOpen(true)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  async function handleLogout() {
    try {
      await logoutRequest()
    } catch {
      // 로그아웃 API 호출이 실패해도 클라이언트 쪽 토큰은 지우고 진행한다.
    } finally {
      clearAuth()
      // navigate()는 App을 리마운트시키지 않는 SPA 전환이라, 강제
      // 로그아웃(api/client.ts)이 쓰는 sessionStorage 플래그 방식으론
      // 토스트가 뜨지 않는다 - App이 내려준 콜백을 바로 호출한다.
      onLoggedOut()
      navigate('/', { replace: true })
    }
  }

  return (
    <header className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-7xl items-center gap-6 px-4 py-3">
        <NavLink to="/" className="flex items-center gap-2">
          <svg
            width="26"
            height="26"
            viewBox="0 0 250 250"
            className="shrink-0"
            style={{ filter: 'drop-shadow(0 1px 2px rgba(16,24,40,0.28))' }}
          >
            <defs>
              <radialGradient id="header-logo-lime" cx="38%" cy="32%" r="75%">
                <stop offset="0%" stopColor="#d7ef7a" />
                <stop offset="55%" stopColor="#8fc23e" />
                <stop offset="100%" stopColor="#4d8a1f" />
              </radialGradient>
              <linearGradient id="header-logo-leaf" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stopColor="#5aa628" />
                <stop offset="100%" stopColor="#2f5f16" />
              </linearGradient>
              <linearGradient id="header-logo-glass" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stopColor="rgba(255,255,255,0.85)" />
                <stop offset="100%" stopColor="rgba(180,225,140,0.32)" />
              </linearGradient>
            </defs>
            <rect x="18" y="18" width="214" height="214" rx="58" fill="url(#header-logo-glass)" stroke="rgba(16,24,40,0.14)" strokeWidth="3" />
            <path d="M40,70 L95,30 L120,30 L55,95 Z" fill="rgba(255,255,255,0.4)" />
            <circle cx="125" cy="130" r="80" fill="url(#header-logo-lime)" />
            <ellipse cx="148" cy="92" rx="24" ry="9" fill="#eaf8b0" opacity=".3" transform="rotate(-30 148 92)" />
            <g transform="translate(172,64) rotate(18) scale(.92)">
              <path
                d="M0,-38 C21,-38 33,-10 33,9 C33,29 19,44 0,44 C-19,44 -33,29 -33,9 C-33,-10 -21,-38 0,-38 Z"
                fill="url(#header-logo-leaf)"
              />
              <path d="M0,-32 L0,38" stroke="#1f4a10" strokeWidth="2" opacity=".5" />
            </g>
          </svg>
          <span className="font-logo text-lg font-bold tracking-tight text-gray-900">퀀트라임</span>
        </NavLink>

        <nav className="flex items-center gap-1">
          <NavLink to="/" end className={navLinkClassName}>
            홈
          </NavLink>
          <NavLink to="/feed" className={navLinkClassName}>
            피드
          </NavLink>
        </nav>

        <div className="flex-1" />

        <button
          type="button"
          onClick={() => setSearchOpen(true)}
          className="flex h-9 w-56 items-center gap-2 rounded-lg bg-gray-200 px-3.5 text-sm text-gray-500 transition hover:bg-gray-300"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="7" />
            <path d="m21 21-4.3-4.3" />
          </svg>
          <span className="flex items-center gap-1.5">
            <kbd className="rounded bg-gray-300 px-1.5 py-0.5 font-mono text-[11px] font-semibold text-gray-600">
              /
            </kbd>
            를 눌러 검색하세요
          </span>
        </button>

        {isAuthenticated ? (
          <ProfileMenu onLogout={() => void handleLogout()} />
        ) : (
          <div className="relative">
            <button
              type="button"
              onClick={() => setLoginModalOpen((prev) => !prev)}
              className="rounded-lg bg-brand-lime px-4 py-1.5 text-sm font-semibold text-gray-900 transition hover:brightness-95"
            >
              로그인
            </button>
            <LoginModal open={loginModalOpen} onClose={() => setLoginModalOpen(false)} />
          </div>
        )}
      </div>

      <SearchOverlay open={searchOpen} onClose={() => setSearchOpen(false)} />
    </header>
  )
}
