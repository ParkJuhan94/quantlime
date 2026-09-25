import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  useMarkAllNotificationsAsRead,
  useMarkNotificationAsRead,
  useNotificationsQuery,
  useUnreadNotificationCountQuery,
} from '../../hooks/queries/useNotifications'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const unreadCountQuery = useUnreadNotificationCountQuery()
  // 목록은 드롭다운을 열었을 때만 조회한다(배지 카운트는 항상 폴링).
  const notificationsQuery = useNotificationsQuery(0, open)
  const markAsRead = useMarkNotificationAsRead()
  const markAllAsRead = useMarkAllNotificationsAsRead()

  useEffect(() => {
    if (!open) return
    function handleClickOutside(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    window.addEventListener('mousedown', handleClickOutside)
    return () => window.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  const unreadCount = unreadCountQuery.data?.unreadCount ?? 0
  const notifications = notificationsQuery.data?.content ?? []

  function handleSelect(notificationId: number, isRead: boolean, linkUrl: string | null) {
    if (!isRead) {
      markAsRead.mutate(notificationId)
    }
    setOpen(false)
    if (linkUrl) {
      navigate(linkUrl)
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label="알림 열기"
        className="relative rounded-full p-2 text-gray-500 transition hover:bg-gray-100"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        {unreadCount > 0 && <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-gray-900" />}
      </button>

      {open && (
        <div className="absolute right-0 top-11 z-50 w-80 rounded-2xl border border-gray-100 bg-white p-3 shadow-lg">
          <div className="flex items-center justify-between px-1 pb-2">
            <span className="text-sm font-semibold text-gray-900">알림</span>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllAsRead.mutate()}
                className="text-xs font-medium text-gray-500 hover:text-gray-700"
              >
                전체 읽음
              </button>
            )}
          </div>

          <div className="max-h-96 divide-y divide-gray-100 overflow-y-auto border-t border-gray-100">
            {notifications.length === 0 && (
              <p className="px-1 py-6 text-center text-sm text-gray-400">받은 알림이 없어요.</p>
            )}
            {notifications.map((notification) => (
              <button
                key={notification.id}
                type="button"
                onClick={() => handleSelect(notification.id, notification.isRead, notification.linkUrl)}
                className={`flex w-full flex-col gap-0.5 rounded-lg px-2 py-2 text-left transition hover:bg-gray-100 ${
                  notification.isRead ? 'text-gray-500' : 'bg-gray-50 text-gray-900'
                }`}
              >
                <span className="text-sm font-semibold">{notification.title}</span>
                <span className="text-xs">{notification.content}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
