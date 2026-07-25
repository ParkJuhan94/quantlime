import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useBacktestQuery } from '../hooks/queries/useBacktest'
import { useStockDetailQuery } from '../hooks/queries/useStockDetail'
import { LoadingSpinner } from '../components/common/LoadingSpinner'
import { ErrorState } from '../components/common/ErrorState'
import { EmptyState } from '../components/common/EmptyState'
import { getErrorMessage, isNotFoundStatus } from '../api/errors'
import { BacktestPriceScoreChart } from '../components/backtest/BacktestPriceScoreChart'
import { QuadrantSummary } from '../components/backtest/QuadrantSummary'
import { ScoreHistogram } from '../components/backtest/ScoreHistogram'
import { RankIcBars } from '../components/backtest/RankIcBars'
import { QuantileBucketTable } from '../components/backtest/QuantileBucketTable'
import { BacktestDisclaimer } from '../components/backtest/BacktestDisclaimer'
import type { BacktestAxisCode } from '../types/backtest'

const AXIS_TABS: { code: BacktestAxisCode; label: string }[] = [
  { code: 'TREND', label: '추세추종' },
  { code: 'MEAN_REVERSION', label: '평균회귀' },
]

export function BacktestPage() {
  const { stockCode = '' } = useParams<{ stockCode: string }>()
  const [axis, setAxis] = useState<BacktestAxisCode>('TREND')

  const detailQuery = useStockDetailQuery(stockCode)
  const backtestQuery = useBacktestQuery(stockCode)

  if (backtestQuery.isLoading) {
    return <LoadingSpinner />
  }
  if (backtestQuery.isError && isNotFoundStatus(backtestQuery.error)) {
    return <EmptyState message="아직 계산된 백테스트 결과가 없습니다." />
  }
  if (backtestQuery.isError) {
    return <ErrorState message={getErrorMessage(backtestQuery.error, '백테스트 결과를 불러오지 못했습니다.')} />
  }
  if (!backtestQuery.data) {
    return null
  }

  const backtest = backtestQuery.data
  const activeAxisData = backtest.axes.find((a) => a.axis === axis)

  return (
    <div className="space-y-6">
      <section>
        <Link to={`/stocks/${stockCode}`} className="text-xs text-gray-500 hover:underline">
          ← 종목 상세로
        </Link>
        <h1 className="mt-1 text-xl font-bold text-gray-900">
          {detailQuery.data?.stockName ?? stockCode} 백테스트
        </h1>
        <p className="text-xs text-gray-400">
          스코어 버전 {backtest.scoreVersion} · 최근 계산일 {backtest.backtestDate ?? '-'} · 표본{' '}
          {backtest.dailyScores.length}일
        </p>
      </section>

      <BacktestPriceScoreChart dailyScores={backtest.dailyScores} />

      <QuadrantSummary dailyScores={backtest.dailyScores} />

      <section className="rounded-xl border border-gray-200 bg-white p-4">
        <p className="mb-3 text-xs font-semibold text-gray-700">스코어 분포</p>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <ScoreHistogram label="추세추종" color="#7c3aed" scores={backtest.dailyScores.map((d) => d.trendScore)} />
          <ScoreHistogram
            label="평균회귀"
            color="#0d9488"
            scores={backtest.dailyScores.map((d) => d.meanReversionScore)}
          />
        </div>
      </section>

      <section className="rounded-xl border border-gray-200 bg-white p-4">
        <div className="mb-3 flex items-center justify-between">
          <p className="text-xs font-semibold text-gray-700">Rank IC · 분위수 버킷</p>
          <div className="flex gap-1">
            {AXIS_TABS.map((tab) => (
              <button
                key={tab.code}
                type="button"
                onClick={() => setAxis(tab.code)}
                className={`rounded-lg px-3 py-1 text-xs font-medium transition ${
                  axis === tab.code ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>
        {activeAxisData ? (
          <>
            <RankIcBars horizons={activeAxisData.horizons} />
            <div className="mt-4 border-t border-gray-100 pt-4">
              <QuantileBucketTable horizons={activeAxisData.horizons} />
            </div>
            <p className="mt-3 text-[11px] text-gray-400">
              스코어 자기상관{' '}
              {activeAxisData.scoreAutocorrelation != null ? activeAxisData.scoreAutocorrelation.toFixed(2) : '-'}
              {' · '}
              등급 변동률{' '}
              {activeAxisData.gradeFlipRate != null ? `${(activeAxisData.gradeFlipRate * 100).toFixed(0)}%` : '-'}
            </p>
          </>
        ) : (
          <EmptyState message="해당 축의 백테스트 데이터가 없습니다." />
        )}
      </section>

      <BacktestDisclaimer />
    </div>
  )
}
