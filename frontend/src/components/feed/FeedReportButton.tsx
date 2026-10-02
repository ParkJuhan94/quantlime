import { useState } from 'react'
import { useAuth } from '../../auth/useAuth'
import { getErrorMessage } from '../../api/errors'
import { LoginModal } from '../auth/LoginModal'
import { FEED_REPORT_REASONS, type FeedReportReason } from '../../types/feed'

interface FeedReportButtonProps {
  // 글/댓글 신고 API 호출 - 실패하면 던진다(메시지를 그대로 보여준다).
  onReport: (reason: FeedReportReason) => Promise<unknown>
}

// "신고" → 사유 선택 → 완료 안내의 3단계를 인라인으로 처리한다(별도 모달을 열 만큼 무거운 동작이 아님).
// 서버는 같은 사용자의 중복 신고를 조용히 무시하므로 성공 문구는 항상 동일하게 보여준다.
export function FeedReportButton({ onReport }: FeedReportButtonProps) {
  const { isAuthenticated } = useAuth()
  const [picking, setPicking] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loginModalOpen, setLoginModalOpen] = useState(false)

  async function handlePick(reason: FeedReportReason) {
    setError(null)
    try {
      await onReport(reason)
      setPicking(false)
      setDone(true)
    } catch (e) {
      setError(getErrorMessage(e, '신고에 실패했어요. 다시 시도해주세요.'))
    }
  }

  if (done) {
    return <span className="text-xs text-gray-400">신고했어요</span>
  }

  return (
    <>
      {picking ? (
        <div className="flex flex-wrap items-center gap-1 text-xs">
          {FEED_REPORT_REASONS.map((option) => (
            <button
              key={option.key}
              type="button"
              onClick={() => void handlePick(option.key)}
              className="rounded-lg bg-gray-100 px-2 py-0.5 text-gray-600 transition hover:bg-gray-200"
            >
              {option.label}
            </button>
          ))}
          <button
            type="button"
            onClick={() => setPicking(false)}
            className="rounded px-1.5 py-0.5 text-gray-400 hover:text-gray-600"
          >
            취소
          </button>
          {error && <span className="text-red-600">{error}</span>}
        </div>
      ) : (
        <button
          type="button"
          onClick={() => (isAuthenticated ? setPicking(true) : setLoginModalOpen(true))}
          className="rounded px-1.5 py-0.5 text-xs text-gray-400 transition hover:bg-gray-50 hover:text-gray-600"
        >
          신고
        </button>
      )}
      <LoginModal open={loginModalOpen} onClose={() => setLoginModalOpen(false)} />
    </>
  )
}
