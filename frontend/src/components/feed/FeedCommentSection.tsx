import { useState } from 'react'
import { useAuth } from '../../auth/useAuth'
import { useMeQuery } from '../../hooks/queries/useMe'
import { useCreateFeedComment, useFeedCommentsQuery } from '../../hooks/queries/useFeed'
import { ProfileAvatar } from '../common/ProfileAvatar'
import { LoginModal } from '../auth/LoginModal'
import { formatRelativeTime } from '../../utils/relativeTime'

// 댓글 목록 조회는 비로그인도 가능하지만(백엔드 GET은 permitAll), 작성은
// 로그인이 필요하다 - FeedComposerCard와 동일하게 비로그인 상태에서
// 등록을 누르면 로그인 모달을 띄운다.
export function FeedCommentSection({ postId }: { postId: number }) {
  const { isAuthenticated } = useAuth()
  const meQuery = useMeQuery(isAuthenticated)
  const commentsQuery = useFeedCommentsQuery(postId, true)
  const createComment = useCreateFeedComment(postId)
  const [content, setContent] = useState('')
  const [loginModalOpen, setLoginModalOpen] = useState(false)

  const comments = commentsQuery.data?.content ?? []

  async function handleSubmit() {
    if (!isAuthenticated) {
      setLoginModalOpen(true)
      return
    }
    if (content.trim().length === 0) return
    await createComment.mutateAsync(content.trim())
    setContent('')
  }

  return (
    <div className="mt-3 border-t border-gray-100 pt-3">
      {commentsQuery.isLoading && <p className="text-xs text-gray-400">불러오는 중...</p>}
      {!commentsQuery.isLoading && comments.length === 0 && (
        <p className="text-xs text-gray-400">아직 댓글이 없어요. 첫 댓글을 남겨보세요.</p>
      )}
      <ul className="flex flex-col gap-2.5">
        {comments.map((comment) => (
          <li key={comment.id} className="flex items-start gap-2">
            <ProfileAvatar
              profileImageUrl={comment.profileImageUrl}
              nickname={comment.nickname}
              className="h-6 w-6"
              textSizeClassName="text-[10px]"
            />
            <div className="min-w-0 flex-1">
              <p className="text-xs">
                <span className="font-semibold text-gray-900">{comment.nickname}</span>{' '}
                <span className="text-gray-400">· {formatRelativeTime(comment.createdAt)}</span>
              </p>
              <p className="text-xs leading-relaxed text-gray-700">{comment.content}</p>
            </div>
          </li>
        ))}
      </ul>

      <div className="mt-3 flex items-center gap-2">
        <ProfileAvatar
          profileImageUrl={meQuery.data?.profileImageUrl}
          nickname={meQuery.data?.nickname}
          className="h-7 w-7"
          textSizeClassName="text-xs"
        />
        <input
          type="text"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') void handleSubmit()
          }}
          placeholder="댓글을 남겨보세요"
          className="flex-1 rounded-lg bg-gray-50 px-3 py-1.5 text-xs text-gray-900 placeholder-gray-400 outline-none"
        />
        <button
          type="button"
          onClick={() => void handleSubmit()}
          disabled={createComment.isPending}
          className="shrink-0 rounded-lg bg-gray-900 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-40"
        >
          등록
        </button>
      </div>

      <LoginModal open={loginModalOpen} onClose={() => setLoginModalOpen(false)} />
    </div>
  )
}
