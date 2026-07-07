import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { logout as logoutRequest } from '../../api/auth'

export function AppHeader() {
  const { isAuthenticated, clearAuth } = useAuth()
  const navigate = useNavigate()

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
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link to="/" className="text-lg font-bold text-gray-900">
          QuantLab
        </Link>
        <nav className="flex items-center gap-4 text-sm text-gray-600">
          {isAuthenticated ? (
            <>
              <Link to="/" className="hover:text-gray-900">
                관심종목
              </Link>
              <Link to="/dashboard" className="hover:text-gray-900">
                대시보드
              </Link>
              <button type="button" onClick={() => void handleLogout()} className="hover:text-gray-900">
                로그아웃
              </button>
            </>
          ) : (
            <Link to="/login" className="hover:text-gray-900">
              로그인
            </Link>
          )}
        </nav>
      </div>
    </header>
  )
}
