export interface NotificationResponse {
  id: number
  type: string
  title: string
  content: string
  linkUrl: string | null
  isRead: boolean
  createdAt: string
}

export interface UnreadCountResponse {
  unreadCount: number
}

export interface RegisterFcmTokenRequest {
  token: string
  deviceInfo?: string
}
