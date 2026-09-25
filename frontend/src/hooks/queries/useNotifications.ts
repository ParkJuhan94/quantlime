import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsAsRead,
  markNotificationAsRead,
} from '../../api/notification'
import { queryKeys } from '../queryKeys'
import { useAuth } from '../../auth/useAuth'

export function useNotificationsQuery(page = 0, enabled = true) {
  const { isAuthenticated } = useAuth()
  return useQuery({
    queryKey: queryKeys.notifications(page),
    queryFn: () => getNotifications(page),
    enabled: enabled && isAuthenticated,
  })
}

export function useUnreadNotificationCountQuery() {
  const { isAuthenticated } = useAuth()
  return useQuery({
    queryKey: queryKeys.notificationsUnreadCount,
    queryFn: getUnreadNotificationCount,
    enabled: isAuthenticated,
    // 벨 아이콘 배지가 새 알림을 어느 정도 실시간에 가깝게 반영하도록
    // 1분마다 갱신한다(WebSocket 실시간 반영은 이번 스코프 아님).
    refetchInterval: 60 * 1000,
  })
}

export function useMarkNotificationAsRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markNotificationAsRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.notificationsAll })
    },
  })
}

export function useMarkAllNotificationsAsRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markAllNotificationsAsRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.notificationsAll })
    },
  })
}
