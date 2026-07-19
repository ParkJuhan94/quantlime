// 피드 글 작성 시각을 "3분", "2시간", "5일" 처럼 짧게 표시한다(실제
// 토스증권 피드 표기 참고, FeedComposerCard 주석 참고).
export function formatRelativeTime(isoDateTime: string): string {
  const diffMs = Date.now() - new Date(isoDateTime).getTime()
  const diffMinutes = Math.floor(diffMs / 60_000)

  if (diffMinutes < 1) return '방금'
  if (diffMinutes < 60) return `${diffMinutes}분`
  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours}시간`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}일`
}
