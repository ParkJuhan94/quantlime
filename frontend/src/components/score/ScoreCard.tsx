import type { ScoreResponse } from '../../types/score'
import { GradeBadge } from './GradeBadge'
import { DivergenceNotice } from './DivergenceNotice'
import { formatScore } from '../../utils/scoreFormat'

export function ScoreCard({ score }: { score: ScoreResponse }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-900">스코어</h2>
        <GradeBadge grade={score.grade} />
      </div>
      {score.insufficientData && (
        <p className="mb-3 rounded-lg bg-gray-50 px-3 py-2 text-xs text-gray-500">
          데이터가 충분하지 않아 신뢰도가 낮은 스코어입니다.
        </p>
      )}
      <dl className="mb-3 grid grid-cols-3 gap-3 text-center text-sm">
        <div>
          <dt className="text-xs text-gray-500">추세추종</dt>
          <dd className="font-semibold text-gray-900">{formatScore(score.trendScore)}</dd>
        </div>
        <div>
          <dt className="text-xs text-gray-500">평균회귀</dt>
          <dd className="font-semibold text-gray-900">{formatScore(score.meanReversionScore)}</dd>
        </div>
        <div>
          <dt className="text-xs text-gray-500">종합</dt>
          <dd className="font-semibold text-gray-900">{formatScore(score.compositeScore)}</dd>
        </div>
      </dl>
      <DivergenceNotice message={score.divergenceMessage} />
      <p className="mt-3 text-sm text-gray-600">{score.comment}</p>
    </div>
  )
}
