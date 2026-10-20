// 공지 작성 폼 검증. 길이 한도는 백엔드 CreateNoticeRequest/UpdateNoticeRequest와 같다.
export const NOTICE_LIMITS = { title: 200, content: 5000 } as const

export function validateNoticeForm({ title, content }: { title: string; content: string }): string | null {
  if (!title.trim()) return '제목을 입력해 주세요.'
  if (title.length > NOTICE_LIMITS.title) return `제목은 ${NOTICE_LIMITS.title}자를 넘을 수 없어요.`
  if (!content.trim()) return '내용을 입력해 주세요.'
  if (content.length > NOTICE_LIMITS.content) return `내용은 ${NOTICE_LIMITS.content}자를 넘을 수 없어요.`
  return null
}

// "2026-10-20T09:30:00" -> "2026-10-20" (공지 목록·상세의 날짜 표기)
export function formatNoticeDate(isoDateTime: string): string {
  return isoDateTime.slice(0, 10)
}
