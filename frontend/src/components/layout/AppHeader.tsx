import { useEffect, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { logout as logoutRequest } from '../../api/auth'
import { SearchOverlay } from '../search/SearchOverlay'

const navLinkClassName = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-2.5 py-1.5 text-sm transition hover:bg-gray-100 ${
    isActive ? 'font-semibold text-gray-900' : 'font-medium text-gray-600'
  }`

export function AppHeader() {
  const { isAuthenticated, clearAuth } = useAuth()
  const navigate = useNavigate()
  const [searchOpen, setSearchOpen] = useState(false)

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
      navigate('/login', { replace: true })
    }
  }

  return (
    <header className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-7xl items-center gap-6 px-4 py-3">
        <NavLink to="/" className="flex items-center gap-2">
          <svg width="26" height="26" viewBox="0 0 34 34" className="shrink-0">
            <rect width="34" height="34" rx="9" fill="#3752ff" />
            <circle cx="17" cy="18" r="7" fill="none" stroke="#fff" strokeWidth="2.4" />
            <path d="M20.5 21.5 25 26" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" />
          </svg>
          <span className="font-logo text-lg font-bold tracking-tight text-gray-900">QuantLab</span>
        </NavLink>

        {isAuthenticated && (
          <nav className="flex items-center gap-1">
            <NavLink to="/" end className={navLinkClassName}>
              홈
            </NavLink>
            <NavLink to="/feed" className={navLinkClassName}>
              피드
            </NavLink>
          </nav>
        )}

        <div className="flex-1" />

        {isAuthenticated && (
          <button
            type="button"
            onClick={() => setSearchOpen(true)}
            className="flex h-9 w-56 items-center gap-2 rounded-full bg-gray-100 px-3.5 text-sm text-gray-400 transition hover:bg-gray-200"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="7" />
              <path d="m21 21-4.3-4.3" />
            </svg>
            <span className="flex items-center gap-1.5">
              <kbd className="rounded bg-gray-200 px-1.5 py-0.5 font-mono text-[11px] font-semibold text-gray-500">
                /
              </kbd>
              를 눌러 검색하세요
            </span>
          </button>
        )}

        {isAuthenticated ? (
          <button
            type="button"
            onClick={() => void handleLogout()}
            className="rounded-full px-3 py-1.5 text-sm font-medium text-gray-600 transition hover:bg-gray-100"
          >
            로그아웃
          </button>
        ) : (
          <NavLink
            to="/login"
            className="rounded-full bg-accent px-4 py-1.5 text-sm font-semibold text-white transition hover:opacity-90"
          >
            로그인
          </NavLink>
        )}
      </div>

      <SearchOverlay open={searchOpen} onClose={() => setSearchOpen(false)} />
    </header>
  )
}
