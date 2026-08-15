import { useState } from 'react'
import { useTelegramFeedDetailQuery } from '../../hooks/queries/useTelegramFeed'
import { LoadingSpinner } from '../common/LoadingSpinner'
import { ChannelAvatar } from '../common/ChannelAvatar'
import { TickerChip } from '../common/TickerChip'
import type { TelegramFeedPost } from '../../types/telegramFeed'
import { formatVideoPublishedAt } from '../../utils/dateFilter'

// VideoFeedCard(유튜브 요약)와 구조적으로 동일(Phase 8 P7-F2) - 텔레그램
// 글은 제목이 없어 제목 자리에 "원문 보기" 링크(postUrl, 실제 t.me 글로
// 이동)를 별도로 둔다. 핵심포인트 더보기/접기 애니메이션(grid-template-rows
// 트랜지션)도 동일 패턴을 그대로 재사용.
export function TelegramFeedCard({ post }: { post: TelegramFeedPost }) {
  const [expanded, setExpanded] = useState(false)
  const detailQuery = useTelegramFeedDetailQuery(post.telegramPostId, expanded)

  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-5">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-xs text-gray-500">
          <ChannelAvatar
            name={post.channelName}
            profileImageUrl={post.channelProfileImageUrl}
            channelUrl={post.channelUrl}
          />
          <span>· {formatVideoPublishedAt(post.publishedAt)}</span>
        </div>
        <a
          href={post.postUrl}
          target="_blank"
          rel="noreferrer"
          className="shrink-0 text-xs font-medium text-gray-500 transition hover:text-gray-700 hover:underline"
        >
          원문 보기
        </a>
      </div>

      <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-gray-700">{post.summary}</p>

      {post.tickers.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {post.tickers.map((ticker) => (
            <TickerChip key={ticker.tickerCode} ticker={ticker} />
          ))}
        </div>
      )}

      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        aria-expanded={expanded}
        className={`mt-3 flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-gray-500 transition hover:bg-gray-50 hover:text-gray-700 ${
          expanded ? 'ml-[1em]' : ''
        }`}
      >
        {expanded ? '핵심포인트 접기' : '핵심포인트 더보기'}
        <svg
          width="12"
          height="12"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          className={`transition-transform duration-300 motion-reduce:transition-none ${expanded ? 'rotate-180' : ''}`}
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>

      <div
        className={`grid transition-[grid-template-rows] duration-300 ease-in-out motion-reduce:transition-none ${
          expanded ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'
        }`}
      >
        <div className="overflow-hidden">
          <div className="mt-2 border-t border-gray-100 pt-3">
            {detailQuery.isLoading && <LoadingSpinner />}
            {detailQuery.data && (
              <>
                <ul className="list-disc space-y-1 pl-4 text-sm text-gray-700">
                  {detailQuery.data.keyPoints.map((point, index) => (
                    <li key={index}>{point}</li>
                  ))}
                </ul>
                {detailQuery.data.macroPoints.length > 0 && (
                  <div className="mt-3">
                    <p className="text-xs font-medium text-gray-500">시장 전반</p>
                    <ul className="mt-1 list-disc space-y-1 pl-4 text-sm text-gray-700">
                      {detailQuery.data.macroPoints.map((point, index) => (
                        <li key={index}>{point}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
