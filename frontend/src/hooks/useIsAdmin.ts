import { useAuth } from '../auth/useAuth'
import { useMeQuery } from './queries/useMe'

// 로그인 사용자의 관리자 여부. 화면 노출 판단 전용이다 - 실제 권한은 백엔드가 /api/admin/**를 ROLE_ADMIN으로 막는다.
export function useIsAdmin() {
  const { isAuthenticated } = useAuth()
  const meQuery = useMeQuery(isAuthenticated)
  return {
    isAdmin: meQuery.data?.role === 'ADMIN',
    // 사용자 정보가 아직 오지 않아 관리자 여부를 모르는 상태(이때 곧바로 리다이렉트하면 새로고침이 항상 홈으로 튕긴다)
    isLoading: isAuthenticated && meQuery.isLoading,
  }
}
