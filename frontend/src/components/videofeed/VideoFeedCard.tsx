import { useState } from 'react'
import { useVideoFeedDetailQuery } from '../../hooks/queries/useVideoFeed'
import { LoadingSpinner } from '../common/LoadingSpinner'
import { VideoFeedChannelAvatar } from './VideoFeedChannelAvatar'
import { VideoFeedTickerChip } from './VideoFeedTickerChip'
import type { VideoFeedItem } from '../../types/videoFeed'
import { formatVideoPublishedAt } from '../../utils/dateFilter'

export function VideoFeedCard({ video }: { video: VideoFeedItem }) {
  const [expanded, setExpanded] = useState(false)
  const detailQuery = useVideoFeedDetailQuery(video.videoId, expanded)

  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-5">
      <div className="flex items-center gap-2 text-xs text-gray-500">
        <VideoFeedChannelAvatar
          name={video.channelName}
          profileImageUrl={video.channelProfileImageUrl}
          channelUrl={video.channelUrl}
        />
        <span>· {formatVideoPublishedAt(video.publishedAt)}</span>
      </div>

      {video.videoUrl ? (
        <a
          href={video.videoUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-1.5 block text-sm font-semibold text-gray-900 hover:underline"
        >
          {video.title}
        </a>
      ) : (
        <p className="mt-1.5 text-sm font-semibold text-gray-900">{video.title}</p>
      )}

      <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-gray-700">{video.summary}</p>

      {video.tickers.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {video.tickers.map((ticker) => (
            <VideoFeedTickerChip key={ticker.tickerCode} ticker={ticker} />
          ))}
        </div>
      )}

      {/* "더보기"(3자)/"접기"(2자) 텍스트 길이 차이만큼 버튼이 좌우로
          들썩이던 걸 줄이기 위해 앞부분("핵심포인트")을 공통으로 두고,
          접힌 상태일 때 남는 한 글자 폭만큼(ml-[1em]) 오른쪽으로 밀어
          펼침/접힘 전환 시 버튼이 제자리에 있는 것처럼 보이게 한다. */}
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

      {/* grid-template-rows를 0fr<->1fr로 트랜지션시키는 방식 - 콘텐츠 높이가
          가변(핵심 포인트 개수가 영상마다 다름)이라 height:auto는 애니메이션이
          안 되는데, 이 방식은 JS로 높이를 측정하지 않고도 auto 높이까지
          부드럽게 확장/축소된다. 안쪽 overflow-hidden으로 트랜지션 중간에
          콘텐츠가 삐져나오지 않게 자른다. */}
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
