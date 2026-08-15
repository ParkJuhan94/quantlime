import { useState } from 'react'
import { useTelegramDigestDetailQuery } from '../../hooks/queries/useTelegramFeed'
import { LoadingSpinner } from '../common/LoadingSpinner'
import { ChannelAvatar } from '../common/ChannelAvatar'
import { TickerChip } from '../common/TickerChip'
import type { TelegramFeedDigest } from '../../types/telegramFeed'
import { formatDayLabel } from '../../utils/dateFilter'

// VideoFeedCard(유튜브 요약)와 구조적으로 동일(Phase 8 P7-F2)하되, 텔레그램은
// 채널×날짜 다이제스트(여러 글을 합친 요약, 2026-08-15 재설계)라 원문이
// 하나가 아니다 - 목록 단계에서는 "게시물 N개 종합" 배지만 보여주고, 펼쳤을
// 때(상세 조회) 그날 재료가 된 원문 링크 전부를 핵심포인트 아래에 나열한다.
export function TelegramFeedCard({ digest }: { digest: TelegramFeedDigest }) {
  const [expanded, setExpanded] = useState(false)
  const detailQuery = useTelegramDigestDetailQuery(digest.telegramDigestId, expanded)

  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-5">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-xs text-gray-500">
          <ChannelAvatar
            name={digest.channelName}
            profileImageUrl={digest.channelProfileImageUrl}
            channelUrl={digest.channelUrl}
          />
          <span>· {formatDayLabel(digest.digestDate)}</span>
        </div>
        <span className="shrink-0 text-xs font-medium text-gray-400">게시물 {digest.sourcePostCount}개 종합</span>
      </div>

      <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-gray-700">{digest.summary}</p>

      {digest.tickers.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {digest.tickers.map((ticker) => (
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
                {detailQuery.data.sourcePostUrls.length > 0 && (
                  <div className="mt-3">
                    <p className="text-xs font-medium text-gray-500">원문 보기</p>
                    <div className="mt-1 flex flex-wrap gap-x-2 gap-y-1">
                      {detailQuery.data.sourcePostUrls.map((url, index) => (
                        <a
                          key={url}
                          href={url}
                          target="_blank"
                          rel="noreferrer"
                          className="text-xs text-gray-500 hover:text-gray-700 hover:underline"
                        >
                          원문 {index + 1}
                        </a>
                      ))}
                    </div>
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
