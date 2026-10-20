import { useState } from 'react'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { Toast } from '../../components/common/Toast'
import {
  useCreateNotice,
  useDeleteNotice,
  useNoticesInfiniteQuery,
  useUpdateNotice,
} from '../../hooks/queries/useNotices'
import { describeAdminError } from '../../utils/adminError'
import { formatNoticeDate, NOTICE_LIMITS, validateNoticeForm } from '../../utils/noticeForm'
import { getNotice } from '../../api/notice'

const INPUT_CLASS =
  'w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none focus:border-gray-900'

export function AdminNoticePage() {
  const [editingId, setEditingId] = useState<number | null>(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [sendNotification, setSendNotification] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmCreateOpen, setConfirmCreateOpen] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<{ id: number; title: string } | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const query = useNoticesInfiniteQuery()
  const notices = query.data?.pages.flatMap((page) => page.content) ?? []
  const createNotice = useCreateNotice()
  const updateNotice = useUpdateNotice()
  const deleteNotice = useDeleteNotice()
  const busy = createNotice.isPending || updateNotice.isPending || deleteNotice.isPending
  const editing = editingId !== null

  function resetForm() {
    setEditingId(null)
    setTitle('')
    setContent('')
    setSendNotification(false)
    setError(null)
  }

  // 목록의 미리보기는 잘려 있으므로 수정할 때는 본문 전체를 다시 받아 폼에 채운다.
  async function startEdit(noticeId: number) {
    try {
      const notice = await getNotice(noticeId)
      setEditingId(notice.id)
      setTitle(notice.title)
      setContent(notice.content)
      setSendNotification(false)
      setError(null)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      setToast(describeAdminError(e))
    }
  }

  function validate(): boolean {
    const message = validateNoticeForm({ title, content })
    setError(message)
    return message === null
  }

  async function submit() {
    setConfirmCreateOpen(false)
    try {
      if (editingId !== null) {
        await updateNotice.mutateAsync({
          noticeId: editingId,
          request: { title: title.trim(), content: content.trim() },
        })
        setToast('공지를 수정했어요')
      } else {
        const result = await createNotice.mutateAsync({
          title: title.trim(),
          content: content.trim(),
          sendNotification,
        })
        if (result.notificationRequested && !result.notificationSent) {
          setToast('공지는 등록했지만 알림 발송에 실패했어요. 알림 발송 화면에서 다시 보내 주세요')
        } else {
          setToast(result.notificationSent ? '공지를 등록하고 알림을 보냈어요' : '공지를 등록했어요')
        }
      }
      resetForm()
    } catch (e) {
      setError(describeAdminError(e))
    }
  }

  function handleSubmitClick() {
    if (busy || !validate()) return
    // 전체 알림은 되돌릴 수 없어 확인 창을 거친다(공지 자체만 등록할 때와 수정은 바로 처리)
    if (!editing && sendNotification) setConfirmCreateOpen(true)
    else void submit()
  }

  async function handleDeleteConfirm() {
    if (!pendingDelete) return
    const { id } = pendingDelete
    setPendingDelete(null)
    try {
      await deleteNotice.mutateAsync(id)
      if (editingId === id) resetForm()
      setToast('공지를 삭제했어요')
    } catch (e) {
      setToast(describeAdminError(e))
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <section className="rounded-2xl border border-gray-100 bg-white p-5">
        <h2 className="mb-1 text-base font-bold text-gray-900">
          {editing ? `공지 수정 #${editingId}` : '새 공지 작성'}
        </h2>
        <p className="mb-4 text-sm text-gray-500">
          공지는 로그인 없이도 볼 수 있어요. 본문은 일반 텍스트이고 줄바꿈이 그대로 보여요.
        </p>

        <label className="mb-3 block text-sm font-semibold text-gray-700" htmlFor="admin-notice-title">
          제목
          <input
            id="admin-notice-title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={NOTICE_LIMITS.title}
            placeholder="서비스 점검 안내"
            className={`mt-1 ${INPUT_CLASS}`}
          />
        </label>

        <label className="mb-3 block text-sm font-semibold text-gray-700" htmlFor="admin-notice-content">
          내용
          <textarea
            id="admin-notice-content"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            maxLength={NOTICE_LIMITS.content}
            rows={8}
            placeholder="10월 21일 02:00~03:00 점검이 예정되어 있어요."
            className={`mt-1 resize-y ${INPUT_CLASS}`}
          />
          <span className="mt-1 block text-right text-xs font-normal text-gray-400">
            {content.length}/{NOTICE_LIMITS.content}
          </span>
        </label>

        {editing ? (
          <p className="mb-4 text-xs text-gray-500">
            수정은 알림을 다시 보내지 않아요. 이미 나간 알림은 같은 공지의 최신 내용을 보여줘요.
          </p>
        ) : (
          <label htmlFor="admin-notice-notify" className="mb-4 flex items-center gap-2 text-sm text-gray-700">
            <input
              id="admin-notice-notify"
              type="checkbox"
              checked={sendNotification}
              onChange={(event) => setSendNotification(event.target.checked)}
            />
            등록과 함께 전체 사용자에게 알림 보내기
          </label>
        )}

        {error && (
          <p role="alert" className="mb-3 text-sm font-medium text-red-600">
            {error}
          </p>
        )}

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={handleSubmitClick}
            disabled={busy}
            className="rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-gray-800 disabled:opacity-50"
          >
            {busy ? '처리 중…' : editing ? '수정 저장' : '공지 등록'}
          </button>
          {editing && (
            <button
              type="button"
              onClick={resetForm}
              disabled={busy}
              className="rounded-lg border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              수정 취소
            </button>
          )}
        </div>
      </section>

      <section className="rounded-2xl border border-gray-100 bg-white p-5">
        <h2 className="mb-2 text-base font-bold text-gray-900">등록된 공지</h2>
        {query.isLoading && <p className="py-8 text-center text-sm text-gray-400">불러오는 중…</p>}
        {query.isError && (
          <p role="alert" className="py-8 text-center text-sm text-red-600">
            목록을 불러오지 못했어요. 새로고침해 주세요.
          </p>
        )}
        {!query.isLoading && !query.isError && notices.length === 0 && (
          <p className="py-8 text-center text-sm text-gray-400">등록된 공지가 없어요.</p>
        )}
        {notices.length > 0 && (
          <ul className="divide-y divide-gray-100">
            {notices.map((notice) => (
              <li key={notice.id} className="py-3">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="break-words text-sm font-semibold text-gray-900">{notice.title}</span>
                  <time dateTime={notice.createdAt} className="shrink-0 text-xs text-gray-400">
                    {formatNoticeDate(notice.createdAt)}
                  </time>
                </div>
                <p className="mt-0.5 break-words text-xs text-gray-500">{notice.preview}</p>
                <div className="mt-2 flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => void startEdit(notice.id)}
                    disabled={busy}
                    className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    onClick={() => setPendingDelete({ id: notice.id, title: notice.title })}
                    disabled={busy}
                    className="rounded-lg border border-red-200 px-3 py-1.5 text-xs font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50"
                  >
                    삭제
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
        {query.hasNextPage && (
          <button
            type="button"
            onClick={() => void query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            className="mt-2 w-full rounded-lg border border-gray-200 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            {query.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
          </button>
        )}
      </section>

      <ConfirmDialog
        open={confirmCreateOpen}
        title="공지를 등록하고 알림을 보낼까요?"
        description={`"${title.trim()}" 공지가 등록되고, 모든 사용자에게 알림이 저장되어 푸시로 전달돼요. 알림은 되돌릴 수 없어요.`}
        confirmLabel="등록하고 발송"
        onConfirm={() => void submit()}
        onCancel={() => setConfirmCreateOpen(false)}
      />
      <ConfirmDialog
        open={pendingDelete !== null}
        tone="danger"
        title="공지를 삭제할까요?"
        description={`"${pendingDelete?.title ?? ''}" 공지가 삭제돼요. 이미 나간 알림은 "삭제됐거나 없는 공지예요"로 보여요.`}
        confirmLabel="삭제"
        onConfirm={() => void handleDeleteConfirm()}
        onCancel={() => setPendingDelete(null)}
      />
      {toast && <Toast message={toast} onDismiss={() => setToast(null)} />}
    </div>
  )
}
