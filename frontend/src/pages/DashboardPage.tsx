import { useDashboardScoresQuery } from '../hooks/queries/useDashboardScores'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { ErrorState } from '../components/common/ErrorState'
import { EmptyState } from '../components/common/EmptyState'
import { ScoreRankingTable } from '../components/score/ScoreRankingTable'
import { getErrorMessage } from '../api/errors'

export function DashboardPage() {
  const rankingQuery = useDashboardScoresQuery()

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-gray-900">스코어 대시보드</h1>
      {rankingQuery.isLoading && <LoadingSpinner />}
      {rankingQuery.isError && (
        <ErrorState message={getErrorMessage(rankingQuery.error, '스코어 랭킹을 불러오지 못했습니다.')} />
      )}
      {rankingQuery.data && rankingQuery.data.length === 0 && (
        <EmptyState message="관심 종목이 없거나 아직 계산된 스코어가 없습니다. 관심종목을 먼저 등록해보세요." />
      )}
      {rankingQuery.data && rankingQuery.data.length > 0 && <ScoreRankingTable rankings={rankingQuery.data} />}
    </div>
  )
}
