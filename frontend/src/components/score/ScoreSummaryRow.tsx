import type { CSSProperties } from 'react'
import type { ScoreResponse } from '../../types/score'
import { formatScore } from '../../utils/scoreFormat'

// 등급을 매도~매수 5단계로 표시한다(백엔드 Grade enum과 동일한 5단계,
// com.quantlime.score.domain.Grade 참고). 국내 주식 관례대로 매수 쪽은
// 빨강, 매도 쪽은 파랑 계열로 채색한다. glowColor는 현재 등급 박스에
// 적용하는 은은한 펄스 애니메이션(index.css .animate-glow-pulse) 색상.
const GRADE_SCALE: { label: string; activeClass: string; glowColor: string }[] = [
  { label: '강력매도', activeClass: 'bg-blue-600 text-white', glowColor: 'rgba(37, 99, 235, 0.55)' },
  { label: '매도', activeClass: 'bg-blue-100 text-blue-700', glowColor: 'rgba(37, 99, 235, 0.35)' },
  { label: '중립', activeClass: 'bg-gray-400 text-white', glowColor: 'rgba(107, 114, 128, 0.4)' },
  { label: '매수', activeClass: 'bg-red-100 text-red-700', glowColor: 'rgba(220, 38, 38, 0.35)' },
  { label: '강력매수', activeClass: 'bg-red-600 text-white', glowColor: 'rgba(220, 38, 38, 0.55)' },
]

// 등급 박스 5칸을 위쪽 전체 너비에 걸쳐 넓게 배치하고, 추세추종/평균회귀/
// 코멘트는 그 아래로 내린다(예전엔 박스 옆에 나란히 둬서 박스 폭이
// 좁아졌었다 - 사용자 피드백, 2026-07-16). 세로로 쌓이는 대신 박스 자체가
// 넓어지고 padding도 넉넉해져 종합점수 숫자에 숨 쉴 공간이 생긴다.
export function ScoreSummaryRow({ score }: { score: ScoreResponse }) {
  return (
    <div className="max-w-md flex-1 rounded-2xl border border-gray-100 bg-gradient-to-br from-white to-gray-50 p-4">
      <div className="mb-1.5 flex items-center justify-between">
        <p className="text-xs font-semibold text-gray-700">종합 스코어</p>
        <p className="text-[10px] text-gray-400">최근 거래일 종가 기준</p>
      </div>

      <div className="grid grid-cols-5 gap-2">
        {GRADE_SCALE.map((tier) => {
          const isActive = tier.label === score.grade
          return (
            <div
              key={tier.label}
              style={isActive ? ({ '--glow-color': tier.glowColor } as CSSProperties) : undefined}
              className={`flex flex-col items-center justify-center gap-1 rounded-xl py-3 text-center transition ${
                isActive
                  ? `${tier.activeClass} animate-glow-pulse`
                  : 'border border-gray-200 bg-gray-50 text-gray-500'
              }`}
            >
              <span className="text-[10px] font-medium">{tier.label}</span>
              {isActive && (
                <span className="flex items-baseline gap-0.5">
                  <span className="text-base font-bold">{formatScore(score.compositeScore)}</span>
                  <span className="text-[9px] font-medium opacity-70">/100</span>
                </span>
              )}
            </div>
          )
        })}
      </div>

      <div className="mt-3 flex items-center gap-3 border-t border-gray-100 pt-3">
        <ScoreStat label="추세추종" value={score.trendScore} />
        <span className="h-6 w-px bg-gray-200" />
        <ScoreStat label="평균회귀" value={score.meanReversionScore} />
        <div className="group relative">
          <svg
            width="15"
            height="15"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            className="cursor-help text-gray-300 transition hover:text-gray-400"
          >
            <circle cx="12" cy="12" r="9" />
            <path d="M12 11v5.5" strokeLinecap="round" />
            <circle cx="12" cy="8" r="0.75" fill="currentColor" stroke="none" />
          </svg>
          <div className="invisible absolute left-0 top-6 z-20 w-56 rounded-xl border border-gray-100 bg-white p-3 text-xs leading-relaxed text-gray-600 opacity-0 shadow-lg transition group-hover:visible group-hover:opacity-100">
            퀀트라임의 자체 로직·백테스팅을 통해 산출한 100점 만점 기준의 점수예요.
            세부 산출 방식은 외부에 공개하지 않아요.
            {score.insufficientData && (
              <p className="mt-2 text-[11px] text-gray-400">데이터가 충분하지 않아 신뢰도가 낮아요.</p>
            )}
          </div>
        </div>
      </div>

      {score.comment && <p className="mt-2 line-clamp-2 text-xs leading-snug text-gray-500">{score.comment}</p>}
    </div>
  )
}

function ScoreStat({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="text-left">
      <p className="text-[10px] text-gray-400">{label}</p>
      <p className="text-xs font-semibold text-gray-900">{formatScore(value)}</p>
    </div>
  )
}
