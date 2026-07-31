// 인증 토큰과 달리 민감하지 않은 단순 UI 선호값(예: 영상 요약 조회 날짜)
// 저장용 - httpOnly가 아니라 클라이언트 JS로 직접 읽고 쓴다.
export function getCookie(name: string): string | null {
  const escapedName = name.replace(/([.$?*|{}()[\]\\/+^])/g, '\\$1')
  const match = document.cookie.match(new RegExp(`(?:^|; )${escapedName}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

export function setCookie(name: string, value: string, days: number): void {
  const expires = new Date(Date.now() + days * 86_400_000).toUTCString()
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/; SameSite=Lax`
}
