// 관리자 공지 발송 폼 검증. 길이 한도는 백엔드 BroadcastNotificationRequest/notification 테이블 컬럼과 같다.
export const NOTIFICATION_LIMITS = { title: 100, content: 500, linkUrl: 500 } as const

export interface NotificationFormValues {
  title: string
  content: string
  linkUrl: string
}

// 알림 클릭 시 react-router의 navigate(linkUrl)로 이동하므로 사이트 내부 경로("/feed")만 받는다.
// 외부 주소나 "//host" 형태는 SPA 이동이 아니라 잘못된 경로가 되므로 막는다.
export function validateNotificationForm({ title, content, linkUrl }: NotificationFormValues): string | null {
  if (!title.trim()) return '제목을 입력해 주세요.'
  if (title.length > NOTIFICATION_LIMITS.title) return `제목은 ${NOTIFICATION_LIMITS.title}자를 넘을 수 없어요.`
  if (!content.trim()) return '내용을 입력해 주세요.'
  if (content.length > NOTIFICATION_LIMITS.content) return `내용은 ${NOTIFICATION_LIMITS.content}자를 넘을 수 없어요.`
  const link = linkUrl.trim()
  if (link) {
    if (!link.startsWith('/') || link.startsWith('//')) return '링크는 "/feed"처럼 사이트 안의 경로(/로 시작)여야 해요.'
    if (link.length > NOTIFICATION_LIMITS.linkUrl) return `링크는 ${NOTIFICATION_LIMITS.linkUrl}자를 넘을 수 없어요.`
  }
  return null
}
