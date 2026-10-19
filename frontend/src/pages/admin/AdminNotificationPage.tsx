import { useState } from 'react'
import { broadcastNotification, sendTestNotification } from '../../api/admin'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { Toast } from '../../components/common/Toast'
import { NOTIFICATION_LIMITS, validateNotificationForm } from '../../utils/adminNotification'
import { NOTIFICATION_TONE_CLASSES, getNotificationTypeStyle } from '../../utils/notificationType'

type Sending = 'idle' | 'test' | 'broadcast'

const INPUT_CLASS =
  'w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none focus:border-gray-900'

export function AdminNotificationPage() {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [linkUrl, setLinkUrl] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState<Sending>('idle')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  const noticeStyle = getNotificationTypeStyle('ADMIN_NOTICE')
  const request = { title: title.trim(), content: content.trim(), linkUrl: linkUrl.trim() || undefined }

  function validate(): boolean {
    const message = validateNotificationForm({ title, content, linkUrl })
    setError(message)
    return message === null
  }

  async function handleTest() {
    if (sending !== 'idle' || !validate()) return
    setSending('test')
    try {
      await sendTestNotification(request)
      setToast('내 계정에 테스트 알림을 보냈어요')
    } catch {
      setError('테스트 발송에 실패했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setSending('idle')
    }
  }

  function handleBroadcastClick() {
    if (sending !== 'idle' || !validate()) return
    setConfirmOpen(true)
  }

  async function handleBroadcastConfirm() {
    setConfirmOpen(false)
    setSending('broadcast')
    try {
      await broadcastNotification(request)
      setToast('전체 사용자에게 공지를 발송했어요')
      setTitle('')
      setContent('')
      setLinkUrl('')
    } catch {
      setError('발송에 실패했어요. 이미 일부에게 전달됐을 수 있으니 확인한 뒤 다시 시도해 주세요.')
    } finally {
      setSending('idle')
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <section className="rounded-2xl border border-gray-100 bg-white p-5">
        <h2 className="mb-1 text-base font-bold text-gray-900">공지 발송</h2>
        <p className="mb-4 text-sm text-gray-500">
          먼저 "나에게만 테스트"로 내용과 푸시 도착을 확인한 뒤 전체 발송하세요. 전체 발송은 되돌릴 수 없어요.
        </p>

        <label className="mb-3 block text-sm font-semibold text-gray-700" htmlFor="admin-notification-title">
          제목
          <input
            id="admin-notification-title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={NOTIFICATION_LIMITS.title}
            placeholder="서비스 점검 안내"
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>

        <label className="mb-3 block text-sm font-semibold text-gray-700" htmlFor="admin-notification-content">
          내용
          <textarea
            id="admin-notification-content"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            maxLength={NOTIFICATION_LIMITS.content}
            rows={4}
            placeholder="오늘 02:00~03:00 점검이 예정되어 있어요."
            className={`mt-1 resize-y ${INPUT_CLASS}`}
          />
          <span className="mt-1 block text-right text-xs font-normal text-gray-400">
            {content.length}/{NOTIFICATION_LIMITS.content}
          </span>
        </label>

        <label className="mb-4 block text-sm font-semibold text-gray-700" htmlFor="admin-notification-link">
          링크 (선택)
          <input
            id="admin-notification-link"
            value={linkUrl}
            onChange={(event) => setLinkUrl(event.target.value)}
            maxLength={NOTIFICATION_LIMITS.linkUrl}
            placeholder="/feed"
            className={`mt-1 ${INPUT_CLASS}`}
          />
          <span className="mt-1 block text-xs font-normal text-gray-400">
            알림을 눌렀을 때 이동할 사이트 안의 경로예요. 비워 두면 이동하지 않아요.
          </span>
        </label>

        {error && (
          <p role="alert" className="mb-3 text-sm font-medium text-red-600">
            {error}
          </p>
        )}

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void handleTest()}
            disabled={sending !== 'idle'}
            className="rounded-lg border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            {sending === 'test' ? '보내는 중…' : '나에게만 테스트'}
          </button>
          <button
            type="button"
            onClick={handleBroadcastClick}
            disabled={sending !== 'idle'}
            className="rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {sending === 'broadcast' ? '발송 중…' : '전체 사용자에게 발송'}
          </button>
        </div>
      </section>

      <section className="rounded-2xl border border-gray-100 bg-white p-5">
        <h2 className="mb-3 text-sm font-semibold text-gray-500">미리보기</h2>
        <div className="flex max-w-sm flex-col gap-0.5 rounded-lg bg-gray-50 px-2 py-2 text-gray-900">
          <span
            className={`self-start rounded-md px-1.5 py-0.5 text-xs font-semibold ${NOTIFICATION_TONE_CLASSES[noticeStyle.tone]}`}
          >
            {noticeStyle.label}
          </span>
          <span className="text-sm font-semibold">{title || '제목이 여기에 표시돼요'}</span>
          <span className="text-xs">{content || '내용이 여기에 표시돼요'}</span>
        </div>
      </section>

      <ConfirmDialog
        open={confirmOpen}
        title="전체 사용자에게 발송할까요?"
        description={`"${request.title}" 공지가 모든 사용자에게 알림으로 저장되고 푸시로 전달돼요. 보내면 되돌릴 수 없어요.`}
        confirmLabel="발송"
        onConfirm={() => void handleBroadcastConfirm()}
        onCancel={() => setConfirmOpen(false)}
      />
      {toast && <Toast message={toast} onDismiss={() => setToast(null)} />}
    </div>
  )
}
