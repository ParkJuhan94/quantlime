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
