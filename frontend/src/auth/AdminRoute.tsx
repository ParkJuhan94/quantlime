import { Navigate, Outlet } from 'react-router-dom'
import { useIsAdmin } from '../hooks/useIsAdmin'
import { useAuth } from './useAuth'

// 관리자만 들어가는 화면. 일반 사용자가 주소를 직접 입력하면 홈으로 보낸다(화면 가드일 뿐 -
// 관리자 API는 백엔드가 ROLE_ADMIN으로 따로 막는다).
export function AdminRoute() {
  const { isAuthenticated } = useAuth()
  const { isAdmin, isLoading } = useIsAdmin()
  if (!isAuthenticated) {
    return <Navigate to="/" replace />
  }
  if (isLoading) {
    return null
  }
  if (!isAdmin) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
