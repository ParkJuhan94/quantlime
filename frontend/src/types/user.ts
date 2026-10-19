export interface UserMeResponse {
  nickname: string
  email: string | null
  profileImageUrl: string | null
  // 관리자 메뉴·화면 노출 여부에만 쓴다 - 실제 권한은 백엔드가 JWT로 판정(/api/admin/** -> ROLE_ADMIN)
  role: 'USER' | 'ADMIN'
}
