export interface NotificationStock {
  code: string
  name: string
}

export interface NotificationResponse {
  id: number
  type: string
  title: string
  content: string
  linkUrl: string | null
  isRead: boolean
  createdAt: string
  // 랭킹·사분면 알림의 종목 목록 - 종목명마다 상세(/stocks/{code}) 링크가 된다. 없으면 빈 배열
  stocks: NotificationStock[]
}

export interface UnreadCountResponse {
  unreadCount: number
}

export interface RegisterFcmTokenRequest {
  token: string
  deviceInfo?: string
}
