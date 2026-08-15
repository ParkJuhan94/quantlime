import { formatDayLabel } from '../../utils/dateFilter'

interface DateStepperProps {
  selectedDate: string
  canGoPrev: boolean
  canGoNext: boolean
  onPrev: () => void
  onNext: () => void
}

// VideoFeedPage(유튜브 요약)에서 추출한 날짜 이전/다음 네비게이션 UI(Phase 8
// P7-F1) - 텔레그램 요약 피드도 동일한 UI를 쓴다. 이전/다음 버튼을 날짜
// 텍스트 양옆에 각각 떨어뜨려두면 클릭할 때마다 손이 날짜 텍스트를 가로질러
// 이동해야 해 불편하다는 피드백(2026-07-31)으로 두 버튼을 하나의 테두리
// 안에 서로 붙여 묶고, 날짜 표기는 그 왼쪽에 별도로 둔다.
export function DateStepper({ selectedDate, canGoPrev, canGoNext, onPrev, onNext }: DateStepperProps) {
  return (
    <div className="flex items-center gap-2">
      <span className="min-w-[104px] text-right text-xs font-medium text-gray-700">
        {formatDayLabel(selectedDate)}
      </span>

      <div className="flex items-center overflow-hidden rounded-lg border border-gray-200">
        <button
          type="button"
          disabled={!canGoPrev}
          onClick={onPrev}
          aria-label="하루 전"
          className="px-2 py-1.5 text-gray-500 transition hover:bg-gray-100 hover:text-gray-700 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="m15 18-6-6 6-6" />
          </svg>
        </button>

        <div className="h-4 w-px bg-gray-200" />

        <button
          type="button"
          disabled={!canGoNext}
          onClick={onNext}
          aria-label="하루 다음"
          className="px-2 py-1.5 text-gray-500 transition hover:bg-gray-100 hover:text-gray-700 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="m9 18 6-6-6-6" />
          </svg>
        </button>
      </div>
    </div>
  )
}
