import { useState } from 'react'
import { FeedComposerCard } from '../components/feed/FeedComposerCard'
import { FeedPostCard } from '../components/feed/FeedPostCard'
import { FeedTopicSidebar } from '../components/feed/FeedTopicSidebar'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { EmptyState } from '../components/common/EmptyState'
import { useFeedPostsQuery } from '../hooks/queries/useFeed'
import type { FeedFilter } from '../mock/feedMock'

// 커뮤니티 필터링은 오른쪽 FeedTopicSidebar에만 둔다(게시글 영역엔
// 중복으로 탭을 두지 않음). 글쓰기·조회·좋아요·댓글 전부 실제 백엔드
// 연동 완료(2026-07-16).
export function FeedPage() {
  const [filter, setFilter] = useState<FeedFilter>('전체')
  const postsQuery = useFeedPostsQuery(filter === '전체' ? undefined : filter)
  const posts = postsQuery.data?.content ?? []

  return (
    <div className="mx-auto flex max-w-4xl gap-6 px-2">
      <div className="min-w-0 flex-1 space-y-4">
        <FeedComposerCard />

        {postsQuery.isLoading && <LoadingSpinner />}
        {!postsQuery.isLoading && posts.length === 0 && (
          <EmptyState message="아직 글이 없어요. 첫 글을 남겨보세요!" />
        )}

        <div className="space-y-3">
          {posts.map((post) => (
            <FeedPostCard key={post.id} post={post} />
          ))}
        </div>
      </div>

      <FeedTopicSidebar active={filter} onSelect={setFilter} />
    </div>
  )
}
