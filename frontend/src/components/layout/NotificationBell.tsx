import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  useMarkAllNotificationsAsRead,
  useMarkNotificationAsRead,
  useNotificationsInfiniteQuery,
  useUnreadNotificationCountQuery,
} from '../../hooks/queries/useNotifications'
import { splitContentByStocks } from '../../utils/notificationContent'
import { getNotificationTypeStyle, NOTIFICATION_TONE_CLASSES } from '../../utils/notificationType'
import { formatRelativeTime } from '../../utils/relativeTime'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const unreadCountQuery = useUnreadNotificationCountQuery()
  // 목록은 드롭다운을 열었을 때만 조회한다(배지 카운트는 항상 폴링).
  const notificationsQuery = useNotificationsInfiniteQuery(open)
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
  const notifications = notificationsQuery.data?.pages.flatMap((page) => page.content) ?? []
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = notificationsQuery

  // 목록 맨 끝의 감시 요소가 스크롤 영역 안으로 들어오면 다음 페이지를 받는다.
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!open || !sentinel || !hasNextPage) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && !isFetchingNextPage) void fetchNextPage()
      },
      { root: listRef.current },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [open, hasNextPage, isFetchingNextPage, fetchNextPage, notifications.length])

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

          <div ref={listRef} className="max-h-96 divide-y divide-gray-100 overflow-y-auto border-t border-gray-100">
            {notifications.length === 0 && (
              <p className="px-1 py-6 text-center text-sm text-gray-400">받은 알림이 없어요.</p>
            )}
            {notifications.map((notification) => (
              // 알림 전체를 누를 수 있게 제목 버튼을 카드 크기로 펼치고(after:absolute), 본문 속 종목명 버튼은 그 위(z-10)에
              // 띄운다 - 버튼 안에 버튼을 넣을 수 없어서 쓰는 stretched-link 방식이다. 제목 버튼은 전역 hover filter(index.css)를
              // 꺼야 한다: filter가 걸리면 그 버튼이 absolute 자식(펼친 영역)의 기준 박스가 돼 영역이 글자 크기로 줄고 호버가
              // 풀렸다 다시 걸리는 깜빡임이 생긴다.
              <div
                key={notification.id}
                className={`relative flex flex-col gap-0.5 rounded-lg px-2 py-2 transition hover:bg-gray-100 ${
                  notification.isRead ? 'text-gray-500' : 'bg-gray-50 text-gray-900'
                }`}
              >
                <span
                  className={`self-start rounded-md px-1.5 py-0.5 text-xs font-semibold ${
                    NOTIFICATION_TONE_CLASSES[getNotificationTypeStyle(notification.type).tone]
                  }`}
                >
                  {getNotificationTypeStyle(notification.type).label}
                </span>
                <span className="flex items-baseline justify-between gap-2">
                  <button
                    type="button"
                    onClick={() => handleSelect(notification.id, notification.isRead, notification.linkUrl)}
                    className="text-left text-sm font-semibold after:absolute after:inset-0 after:content-[''] hover:filter-none!"
                  >
                    {notification.title}
                  </button>
                  <time
                    dateTime={notification.createdAt}
                    title={notification.createdAt.replace('T', ' ')}
                    className="shrink-0 text-xs text-gray-400"
                  >
                    {formatRelativeTime(notification.createdAt)}
                  </time>
                </span>
                {/* 배포 중 옛 백엔드 응답(stocks 없음)에도 깨지지 않게 ?? [] 로 받는다 */}
                <p className="text-xs">
                  {splitContentByStocks(notification.content, notification.stocks ?? []).map((segment, index) =>
                    segment.kind === 'stock' ? (
                      <button
                        // oxlint-disable-next-line react/no-array-index-key -- 본문을 쪼갠 조각은 위치로만 구분되고(안정 id가 없음) 알림이 바뀌면 통째로 다시 그려진다
                        key={index}
                        type="button"
                        onClick={() => handleSelect(notification.id, notification.isRead, `/stocks/${segment.code}`)}
                        className="relative z-10 rounded font-semibold text-gray-900 hover:bg-gray-200"
                      >
                        {segment.text}
                      </button>
                    ) : (
                      // oxlint-disable-next-line react/no-array-index-key -- 위와 같은 이유(본문 조각은 위치가 곧 식별자)
                      <span key={index}>{segment.text}</span>
                    ),
                  )}
                </p>
              </div>
            ))}
            {hasNextPage && (
              <div ref={sentinelRef} className="py-3 text-center text-xs text-gray-400">
                {isFetchingNextPage ? '불러오는 중…' : ''}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
