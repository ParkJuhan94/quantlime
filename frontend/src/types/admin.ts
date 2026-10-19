export interface AdminFeedPost {
  id: number
  nickname: string
  category: string
  title: string
  imageUrl: string | null
  reportCount: number
  createdAt: string
}

export interface AdminFeedComment {
  id: number
  postId: number
  nickname: string
  content: string
  reportCount: number
  createdAt: string
}
