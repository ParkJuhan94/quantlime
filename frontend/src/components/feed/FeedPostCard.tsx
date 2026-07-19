import { useState } from 'react'
import { useAuth } from '../../auth/useAuth'
import { useDeleteFeedPost, useLikeFeedPost, useUnlikeFeedPost } from '../../hooks/queries/useFeed'
import { LoginModal } from '../auth/LoginModal'
import { FeedCommentSection } from './FeedCommentSection'
import { FeedComposeModal } from './FeedComposeModal'
import type { FeedPostResponse } from '../../types/feed'
import { formatRelativeTime } from '../../utils/relativeTime'
import { resolveUploadUrl } from '../../utils/uploadUrl'

const AVATAR_COLORS = [
  'bg-blue-100 text-blue-700',
  'bg-pink-100 text-pink-700',
  'bg-gray-200 text-gray-600',
  'bg-purple-100 text-purple-700',
  'bg-amber-100 text-amber-700',
  'bg-emerald-100 text-emerald-700',
]

function avatarColorClass(nickname: string): string {
  const hash = nickname.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

export function FeedPostCard({ post }: { post: FeedPostResponse }) {
  const { isAuthenticated } = useAuth()
  const [imageFailed, setImageFailed] = useState(false)
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  const [commentsOpen, setCommentsOpen] = useState(false)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [deleteConfirming, setDeleteConfirming] = useState(false)
  const likePost = useLikeFeedPost()
  const unlikePost = useUnlikeFeedPost()
  const deletePost = useDeleteFeedPost()

  // 프로필 사진이 있으면 그대로 보여주고, 없거나 깨졌으면(onError) 예전처럼
  // 닉네임별 색상이 다른 이니셜 원으로 대체한다(2026-07-16 - 백엔드는 이미
  // profileImageUrl을 내려주고 있었는데 이 카드만 안 쓰고 있었음).
  const showImage = post.profileImageUrl && !imageFailed

  function handleLikeClick() {
    if (!isAuthenticated) {
      setLoginModalOpen(true)
      return
    }
    if (post.likedByMe) {
      unlikePost.mutate(post.id)
    } else {
      likePost.mutate(post.id)
    }
  }

  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-5">
      <div className="mb-2.5 flex items-start justify-between gap-2.5">
        <div className="flex items-center gap-2.5">
          <div
            className={`flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-semibold ${showImage ? '' : avatarColorClass(post.nickname)}`}
          >
            {showImage ? (
              <img
                src={post.profileImageUrl ?? undefined}
                alt=""
                className="h-full w-full object-cover"
                onError={() => setImageFailed(true)}
              />
            ) : (
              post.nickname.charAt(0)
            )}
          </div>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-900">
              {post.nickname} <span className="font-normal text-gray-400">· {formatRelativeTime(post.createdAt)}</span>
            </p>
            <p className="text-xs font-medium text-gray-500">{post.category}</p>
          </div>
        </div>

        {/* 내가 쓴 글에만 수정/삭제를 노출한다(2026-07-17 피드백). 삭제는
            바로 지우지 않고 한 번 더 확인받는다 - 되돌릴 수 없는 동작이라
            버튼 자체를 "정말 삭제할까요?" 확인 문구로 바꿔치기한다. */}
        {post.mine && (
          <div className="flex shrink-0 items-center gap-1 text-xs text-gray-400">
            {deleteConfirming ? (
              <>
                <span>정말 삭제할까요?</span>
                <button
                  type="button"
                  onClick={() => setDeleteConfirming(false)}
                  className="rounded px-1.5 py-0.5 hover:bg-gray-50 hover:text-gray-600"
                >
                  취소
                </button>
                <button
                  type="button"
                  disabled={deletePost.isPending}
                  onClick={() => deletePost.mutate(post.id)}
                  className="rounded px-1.5 py-0.5 text-red-600 hover:bg-red-50 disabled:opacity-50"
                >
                  {deletePost.isPending ? '삭제 중...' : '삭제'}
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => setEditModalOpen(true)}
                  className="rounded px-1.5 py-0.5 hover:bg-gray-50 hover:text-gray-600"
                >
                  수정
                </button>
                <button
                  type="button"
                  onClick={() => setDeleteConfirming(true)}
                  className="rounded px-1.5 py-0.5 hover:bg-gray-50 hover:text-gray-600"
                >
                  삭제
                </button>
              </>
            )}
          </div>
        )}
      </div>

      {/* 글쓰기 칸이 textarea로 바뀌면서(2026-07-17) 사용자가 입력한 줄바꿈이
          실제로 보여야 의미가 있다 - whitespace-pre-wrap 없이는 <p> 태그가
          \n을 공백으로 뭉개버린다. */}
      <p className="whitespace-pre-wrap text-sm leading-relaxed text-gray-800">{post.title}</p>

      {post.imageUrl && (
        <img
          src={resolveUploadUrl(post.imageUrl)}
          alt=""
          className="mt-3 max-h-80 w-full rounded-xl object-cover"
        />
      )}

      <div className="mt-3 flex items-center gap-4 text-xs text-gray-400">
        <button
          type="button"
          onClick={handleLikeClick}
          aria-label={post.likedByMe ? '좋아요 취소' : '좋아요'}
          className={`flex items-center gap-1 transition ${post.likedByMe ? 'text-red-600' : 'hover:text-gray-600'}`}
        >
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill={post.likedByMe ? '#dc2626' : 'none'}
            stroke="currentColor"
            strokeWidth="2"
          >
            <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
          </svg>
          {post.likeCount}
        </button>
        <button
          type="button"
          onClick={() => setCommentsOpen((prev) => !prev)}
          className="flex items-center gap-1 transition hover:text-gray-600"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5Z" />
          </svg>
          {post.commentCount}
        </button>
      </div>

      {commentsOpen && <FeedCommentSection postId={post.id} />}

      <LoginModal open={loginModalOpen} onClose={() => setLoginModalOpen(false)} />
      <FeedComposeModal open={editModalOpen} onClose={() => setEditModalOpen(false)} editingPost={post} />
    </div>
  )
}
