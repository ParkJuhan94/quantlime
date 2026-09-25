import { apiClient } from './client'
import type { PageResponse } from '../types/stock'
import type { NotificationResponse, RegisterFcmTokenRequest, UnreadCountResponse } from '../types/notification'

export async function registerFcmToken(request: RegisterFcmTokenRequest): Promise<void> {
  await apiClient.post('/api/notifications/fcm-tokens', request)
}

export async function deleteFcmToken(token: string): Promise<void> {
  await apiClient.delete('/api/notifications/fcm-tokens', { params: { token } })
}

export async function getNotifications(page = 0, size = 10): Promise<PageResponse<NotificationResponse>> {
  const { data } = await apiClient.get<PageResponse<NotificationResponse>>('/api/notifications', {
    params: { page, size },
  })
  return data
}

export async function getUnreadNotificationCount(): Promise<UnreadCountResponse> {
  const { data } = await apiClient.get<UnreadCountResponse>('/api/notifications/unread-count')
  return data
}

export async function markNotificationAsRead(notificationId: number): Promise<void> {
  await apiClient.patch(`/api/notifications/${notificationId}/read`)
}

export async function markAllNotificationsAsRead(): Promise<void> {
  await apiClient.post('/api/notifications/read-all')
}
