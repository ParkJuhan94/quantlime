export interface FeedPostResponse {
  id: number
  nickname: string
  profileImageUrl: string | null
  category: string
  title: string
  imageUrl: string | null
  likeCount: number
  commentCount: number
  likedByMe: boolean
  // 로그인한 사용자 본인이 쓴 글인지 - 수정/삭제 버튼 노출 여부를 결정한다.
  mine: boolean
  createdAt: string
}

export interface FeedCommentResponse {
  id: number
  nickname: string
  profileImageUrl: string | null
  content: string
  createdAt: string
}

// 백엔드 FeedReportReason enum 이름과 일치해야 한다 - label은 화면 표시용.
export type FeedReportReason = 'ADVERTISEMENT' | 'ABUSE' | 'SPAM' | 'FRAUD' | 'OTHER'

export const FEED_REPORT_REASONS: { key: FeedReportReason; label: string }[] = [
  { key: 'ADVERTISEMENT', label: '광고/홍보' },
  { key: 'ABUSE', label: '욕설/비방' },
  { key: 'SPAM', label: '도배/스팸' },
  { key: 'FRAUD', label: '허위 인증/사기' },
  { key: 'OTHER', label: '기타' },
]
