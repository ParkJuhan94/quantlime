import { apiClient } from './client'

export interface AdminNotificationRequest {
  title: string
  content: string
  linkUrl?: string
}

// 호출한 관리자 본인에게만 발송 - 전체 발송 전에 내용과 푸시 도착을 확인하는 용도
export async function sendTestNotification(request: AdminNotificationRequest): Promise<void> {
  await apiClient.post('/api/admin/notifications/test', request)
}

// 모든 사용자에게 발송(인앱 알림 저장 + 푸시)
export async function broadcastNotification(request: AdminNotificationRequest): Promise<void> {
  await apiClient.post('/api/admin/notifications/broadcast', request)
}
