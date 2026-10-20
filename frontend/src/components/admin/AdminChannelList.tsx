import type { ReactNode } from 'react'
import type { AdminChannel } from '../../types/admin'
import { NOTIFICATION_TONE_CLASSES } from '../../utils/notificationType'
import { formatRelativeTime } from '../../utils/relativeTime'

interface AdminChannelListProps {
  title: string
  channels: AdminChannel[] | undefined
  isLoading: boolean
  isError: boolean
  // 채널 한 줄 오른쪽에 붙일 동작(예: 속도 초기화)
  renderAction?: (channel: AdminChannel) => ReactNode
}

export function AdminChannelList({ title, channels, isLoading, isError, renderAction }: AdminChannelListProps) {
  return (
    <section className="rounded-2xl border border-gray-100 bg-white p-5">
      <h3 className="mb-2 text-sm font-bold text-gray-900">{title}</h3>
      {isLoading && <p className="py-6 text-center text-sm text-gray-400">불러오는 중…</p>}
      {isError && (
        <p role="alert" className="py-6 text-center text-sm text-red-600">
          채널 목록을 불러오지 못했어요.
        </p>
      )}
      {channels && channels.length === 0 && <p className="py-6 text-center text-sm text-gray-400">채널이 없어요.</p>}
      {channels && channels.length > 0 && (
        <ul className="divide-y divide-gray-100">
          {channels.map((channel) => (
            <li key={channel.channelId} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 py-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <span className="break-words text-sm font-semibold text-gray-900">{channel.name}</span>
                  <span
                    className={`rounded-md px-1.5 py-0.5 text-xs font-semibold ${
                      NOTIFICATION_TONE_CLASSES[channel.enabled ? 'ok' : 'neutral']
                    }`}
                  >
                    {channel.enabled ? '활성' : '비활성'}
                  </span>
                </div>
                <p className="mt-0.5 text-xs text-gray-500">
                  우선순위 {channel.priority} · 마지막 수집{' '}
                  {channel.lastCollectedAt ? `${formatRelativeTime(channel.lastCollectedAt)} 전` : '없음'}
                  {channel.medianVelocity != null && ` · 중앙값 속도 ${channel.medianVelocity}`}
                </p>
              </div>
              {renderAction?.(channel)}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
