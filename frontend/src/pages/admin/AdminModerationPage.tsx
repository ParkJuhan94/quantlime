import { useState } from 'react'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { Toast } from '../../components/common/Toast'
import {
  useDeleteComment,
  useDeletePost,
  useHiddenCommentsQuery,
  useHiddenPostsQuery,
  useRestoreComment,
  useRestorePost,
} from '../../hooks/queries/useAdminModeration'
import { NOTIFICATION_TONE_CLASSES } from '../../utils/notificationType'
import { formatRelativeTime } from '../../utils/relativeTime'
import { resolveUploadUrl } from '../../utils/uploadUrl'

type Tab = 'posts' | 'comments'
type PendingDelete = { kind: Tab; id: number; summary: string }

const TABS: { value: Tab; label: string }[] = [
  { value: 'posts', label: '숨김 글' },
  { value: 'comments', label: '숨김 댓글' },
]

const ACTION_BASE = 'rounded-lg px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50'

function ReportBadge({ count }: { count: number }) {
  return (
    <span className={`shrink-0 rounded-md px-1.5 py-0.5 text-xs font-semibold ${NOTIFICATION_TONE_CLASSES.danger}`}>
      신고 {count}건
    </span>
  )
}

function RowActions({ busy, onRestore, onDelete }: { busy: boolean; onRestore: () => void; onDelete: () => void }) {
  return (
    <div className="mt-3 flex justify-end gap-2">
      <button
        type="button"
        onClick={onRestore}
        disabled={busy}
        className={`${ACTION_BASE} border border-gray-200 text-gray-700 hover:bg-gray-50`}
      >
        복구
      </button>
      <button
        type="button"
        onClick={onDelete}
        disabled={busy}
        className={`${ACTION_BASE} border border-red-200 text-red-600 hover:bg-red-50`}
      >
        영구 삭제
      </button>
    </div>
  )
}

export function AdminModerationPage() {
  const [tab, setTab] = useState<Tab>('posts')
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const postsQuery = useHiddenPostsQuery()
  const commentsQuery = useHiddenCommentsQuery()
  const restorePost = useRestorePost()
  const restoreComment = useRestoreComment()
  const deletePost = useDeletePost()
  const deleteComment = useDeleteComment()

  const busy = restorePost.isPending || restoreComment.isPending || deletePost.isPending || deleteComment.isPending
  const query = tab === 'posts' ? postsQuery : commentsQuery
  const posts = postsQuery.data?.pages.flatMap((page) => page.content) ?? []
  const comments = commentsQuery.data?.pages.flatMap((page) => page.content) ?? []
  const isEmpty = !query.isLoading && (tab === 'posts' ? posts.length === 0 : comments.length === 0)

  function run(action: Promise<unknown>, success: string) {
    action.then(
      () => setToast(success),
      () => setToast('처리에 실패했어요. 잠시 후 다시 시도해 주세요'),
    )
  }

  function handleDeleteConfirm() {
    if (!pendingDelete) return
    const { kind, id } = pendingDelete
    setPendingDelete(null)
    run(kind === 'posts' ? deletePost.mutateAsync(id) : deleteComment.mutateAsync(id), '영구 삭제했어요')
  }

  return (
    <section className="rounded-2xl border border-gray-100 bg-white p-5">
      <h2 className="mb-1 text-base font-bold text-gray-900">신고 관리</h2>
      <p className="mb-4 text-sm text-gray-500">
        신고가 쌓여 숨겨진 글과 댓글이에요. 문제가 없으면 복구하고, 문제가 맞으면 영구 삭제하세요.
      </p>

      <div role="tablist" aria-label="신고 관리 구분" className="mb-2 inline-flex gap-1 rounded-xl bg-gray-100 p-1">
        {TABS.map((item) => (
          <button
            key={item.value}
            type="button"
            role="tab"
            aria-selected={tab === item.value}
            onClick={() => setTab(item.value)}
            className={`rounded-lg px-4 py-1.5 text-sm font-semibold transition ${
              tab === item.value ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {query.isLoading && <p className="py-10 text-center text-sm text-gray-400">불러오는 중…</p>}
      {query.isError && (
        <p role="alert" className="py-10 text-center text-sm text-red-600">
          목록을 불러오지 못했어요. 새로고침해 주세요.
        </p>
      )}
      {isEmpty && (
        <p className="py-10 text-center text-sm text-gray-400">
          숨겨진 {tab === 'posts' ? '글이' : '댓글이'} 없어요. 깨끗해요.
        </p>
      )}

      {tab === 'posts' && posts.length > 0 && (
        <ul className="divide-y divide-gray-100">
          {posts.map((post) => (
            <li key={post.id} className="py-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-gray-500">
                    <span className="font-semibold text-gray-900">{post.nickname}</span>
                    <span>· {formatRelativeTime(post.createdAt)}</span>
                    <span className={`rounded-md px-1.5 py-0.5 font-semibold ${NOTIFICATION_TONE_CLASSES.neutral}`}>
                      {post.category}
                    </span>
                  </div>
                  <p className="mt-1.5 break-words text-sm font-semibold text-gray-900">{post.title}</p>
                </div>
                <ReportBadge count={post.reportCount} />
              </div>
              {post.imageUrl && (
                <img
                  src={resolveUploadUrl(post.imageUrl)}
                  alt=""
                  className="mt-2 h-24 w-auto max-w-full rounded-lg border border-gray-100 object-cover"
                />
              )}
              <RowActions
                busy={busy}
                onRestore={() => run(restorePost.mutateAsync(post.id), '글을 복구했어요')}
                onDelete={() => setPendingDelete({ kind: 'posts', id: post.id, summary: post.title })}
              />
            </li>
          ))}
        </ul>
      )}

      {tab === 'comments' && comments.length > 0 && (
        <ul className="divide-y divide-gray-100">
          {comments.map((comment) => (
            <li key={comment.id} className="py-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-gray-500">
                    <span className="font-semibold text-gray-900">{comment.nickname}</span>
                    <span>· {formatRelativeTime(comment.createdAt)}</span>
                    <span className={`rounded-md px-1.5 py-0.5 font-semibold ${NOTIFICATION_TONE_CLASSES.neutral}`}>
                      글 #{comment.postId}
                    </span>
                  </div>
                  <p className="mt-1.5 whitespace-pre-wrap break-words text-sm text-gray-900">{comment.content}</p>
                </div>
                <ReportBadge count={comment.reportCount} />
              </div>
              <RowActions
                busy={busy}
                onRestore={() => run(restoreComment.mutateAsync(comment.id), '댓글을 복구했어요')}
                onDelete={() => setPendingDelete({ kind: 'comments', id: comment.id, summary: comment.content })}
              />
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

      <ConfirmDialog
        open={pendingDelete !== null}
        tone="danger"
        title={pendingDelete?.kind === 'posts' ? '글을 영구 삭제할까요?' : '댓글을 영구 삭제할까요?'}
        description={
          pendingDelete?.kind === 'posts'
            ? `"${pendingDelete.summary}" 글과 그 댓글·좋아요·신고 기록이 함께 삭제돼요. 되돌릴 수 없어요.`
            : `"${pendingDelete?.summary ?? ''}" 댓글과 신고 기록이 삭제돼요. 되돌릴 수 없어요.`
        }
        confirmLabel="영구 삭제"
        onConfirm={handleDeleteConfirm}
        onCancel={() => setPendingDelete(null)}
      />
      {toast && <Toast message={toast} onDismiss={() => setToast(null)} />}
    </section>
  )
}
