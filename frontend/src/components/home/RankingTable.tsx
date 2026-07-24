import { useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { StockLogo } from '../common/StockLogo'
import { GRADE_STYLES } from '../score/GradeBadge'
import { changeRateColorClass, formatChangeRate, formatPrice } from '../../utils/priceFormat'
import { formatScore } from '../../utils/scoreFormat'
import { buildStockLogoUrl } from '../../utils/stockLogo'
import { useMarketRankingQuery } from '../../hooks/queries/useMarketRanking'
import { useDashboardScoresQuery } from '../../hooks/queries/useDashboardScores'
import { useStockPriceSocket } from '../../hooks/useStockPriceSocket'
import { useStockPricesQuery } from '../../hooks/queries/useStockPrices'
import { useFlashOnChange } from '../../hooks/useFlashOnChange'
import { rankingFilterStorage } from '../../storage/rankingFilterStorage'
import { useAuth } from '../../auth/useAuth'

interface RankingTableProps {
  watchlistCodes: Set<string>
  onToggleWatch: (stockCode: string) => void
}

export type Scope = 'all' | 'domestic' | 'overseas'
export type SortKey = 'score' | 'amount' | 'gainers' | 'losers'
export type Period = '실시간' | '1일' | '1주일' | '1개월' | '3개월' | '6개월' | '1년'

const SCOPE_OPTIONS: { key: Scope; label: string }[] = [
  { key: 'all', label: '전체' },
  { key: 'domestic', label: '국내' },
  { key: 'overseas', label: '해외' },
]

// 필터 이름과 컬럼은 정확히 이 순서로 통일한다(사용자 요청, 2026-07-16) -
// 어느 필터를 골라도 표는 항상 같은 컬럼(현재가/등락률/스코어/거래대금/
// 산업)을 보여주고, 필터는 정렬 기준만 바꾼다. 데이터가 아직 없는 컬럼은
// 그 필터에서만 "-"로 비워둔다(거짓 숫자를 보여주지 않는다).
const SORT_OPTIONS: { key: SortKey; label: string }[] = [
  { key: 'score', label: '스코어' },
  { key: 'amount', label: '거래대금' },
  { key: 'gainers', label: '급상승' },
  { key: 'losers', label: '급하락' },
]

const PERIOD_OPTIONS: Period[] = ['실시간', '1일', '1주일', '1개월', '3개월', '6개월', '1년']

interface DisplayRow {
  stockCode: string
  stockName: string
  sector: string | null
  logoUrl: string
  price: number | null
  changeRate: number | null
  compositeScore?: number | null
  grade?: string | null
}

function RankingRow({ row, index, isWatched, onToggleWatch }: {
  row: DisplayRow
  index: number
  isWatched: boolean
  onToggleWatch: (stockCode: string) => void
}) {
  const navigate = useNavigate()
  const isFlashing = useFlashOnChange(row.changeRate)
  const flashClass = isFlashing
    ? row.changeRate != null && row.changeRate > 0
      ? 'bg-red-100'
      : 'bg-blue-100'
    : 'bg-transparent'

  // cmd(맥)/ctrl(윈도) 클릭 시 새 탭으로 열게 한다 - 링크가 아니라 tr에
  // onClick으로 navigate를 붙인 구조라 브라우저가 기본으로 지원하는
  // cmd+클릭 새 탭 열기가 안 먹혀서 직접 분기했다(2026-07-17 피드백).
  function handleRowClick(event: React.MouseEvent) {
    const url = `/stocks/${row.stockCode}`
    if (event.metaKey || event.ctrlKey) {
      window.open(url, '_blank')
      return
    }
    navigate(url)
  }

  return (
    <tr onClick={handleRowClick} className="cursor-pointer border-b border-gray-50 hover:bg-gray-50">
      <td className="py-2.5">
        <div className="flex items-center gap-2">
          {/* 종목상세 하트 버튼과 동일하게 border+배경 박스 스타일로
              통일한다(2026-07-17 피드백 - 예전엔 아이콘만 덩그러니 있어
              화면마다 하트가 다르게 보였음). */}
          <button
            type="button"
            aria-label={isWatched ? '관심종목에서 삭제' : '관심종목에 추가'}
            onClick={(event) => {
              event.stopPropagation()
              onToggleWatch(row.stockCode)
            }}
            className={`shrink-0 rounded-lg border p-1 transition ${
              isWatched ? 'border-red-200 bg-red-50 hover:bg-red-100' : 'border-gray-200 hover:bg-gray-50'
            }`}
          >
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill={isWatched ? '#dc2626' : 'none'}
              stroke={isWatched ? '#dc2626' : '#c6c6c6'}
              strokeWidth="2"
            >
              <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
            </svg>
          </button>
          <span className="text-xs font-semibold text-gray-300">{index + 1}</span>
        </div>
      </td>
      <td className="py-2.5">
        <div className="flex items-center gap-2.5">
          <StockLogo logoUrl={row.logoUrl} stockName={row.stockName} className="h-7 w-7" />
          <div>
            <p className="text-sm font-semibold text-gray-900">{row.stockName}</p>
            <p className="text-xs text-gray-400">{row.stockCode}</p>
          </div>
        </div>
      </td>
      <td className="py-2.5 text-right text-sm font-semibold text-gray-900">{formatPrice(row.price)}</td>
      <td className="py-2.5 text-right">
        <span
          className={`rounded px-1.5 py-0.5 text-sm font-light transition-colors duration-[1500ms] ${changeRateColorClass(row.changeRate)} ${flashClass}`}
        >
          {formatChangeRate(row.changeRate)}
        </span>
      </td>
      <td className="py-2.5 text-right">
        {row.compositeScore != null ? (
          // 종목상세 ScoreSummaryRow처럼 등급 색과 스코어 숫자를 하나의
          // 배지 안에 함께 담는다 - 예전엔 등급 배지와 숫자가 따로
          // 떨어져 있어(배지만 색, 숫자는 항상 회색) 등급과 점수가
          // 시각적으로 연결되지 않아 보였다는 피드백(2026-07-17).
          <span
            className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold ${
              row.grade ? (GRADE_STYLES[row.grade] ?? 'bg-gray-100 text-gray-800') : 'bg-gray-100 text-gray-500'
            }`}
          >
            {formatScore(row.compositeScore)}
            {row.grade && <span className="font-normal opacity-80">{row.grade}</span>}
          </span>
        ) : (
          <span className="text-sm text-gray-300">-</span>
        )}
      </td>
      <td className="py-2.5 text-right text-sm text-gray-300">-</td>
      <td className="max-w-[140px] py-2.5 pl-4">
        {/* 업종명이 길면("측정, 시험, 항해, 제어 및 기타 정밀기기 제조업;
            광학기기 제외" 등) 표 전체 너비를 밀어내 레이아웃이 흔들렸다 -
            칼럼 폭을 캡(max-w-[140px])하고 말줄임 처리, 전체 이름은
            title 툴팁으로 확인 가능하게 한다(2026-07-20 피드백). */}
        {row.sector && (
          <span
            title={row.sector}
            className="inline-block max-w-full truncate rounded-lg bg-gray-100 px-2 py-1 align-bottom text-xs font-medium text-gray-600"
          >
            {row.sector}
          </span>
        )}
      </td>
    </tr>
  )
}

export function RankingTable({ watchlistCodes, onToggleWatch }: RankingTableProps) {
  // 마지막으로 고른 필터를 기억해뒀다가 다음 방문에도 그대로 유지한다
  // (2026-07-16, 새로고침마다 초기화되는 게 불편하다는 피드백).
  const [scope, setScopeState] = useState<Scope>(() => rankingFilterStorage.read().scope)
  const [sortKey, setSortKeyState] = useState<SortKey>(() => rankingFilterStorage.read().sortKey)
  const [period, setPeriodState] = useState<Period>(() => rankingFilterStorage.read().period)
  const [watchlistOnly, setWatchlistOnlyState] = useState<boolean>(() => rankingFilterStorage.read().watchlistOnly)
  const { isAuthenticated } = useAuth()

  function setScope(next: Scope) {
    setScopeState(next)
    rankingFilterStorage.write({ scope: next, sortKey, period, watchlistOnly })
  }
  function setSortKey(next: SortKey) {
    setSortKeyState(next)
    rankingFilterStorage.write({ scope, sortKey: next, period, watchlistOnly })
  }
  function setPeriod(next: Period) {
    setPeriodState(next)
    rankingFilterStorage.write({ scope, sortKey, period: next, watchlistOnly })
  }
  function setWatchlistOnly(next: boolean) {
    setWatchlistOnlyState(next)
    rankingFilterStorage.write({ scope, sortKey, period, watchlistOnly: next })
  }

  const isRealMode = sortKey === 'gainers' || sortKey === 'losers'
  const isScoreMode = sortKey === 'score'
  const isAmountMode = sortKey === 'amount'
  // 스코어/급상승/급하락/거래대금 4개 필터 전부 "관심종목만/전체" 토글을
  // 공통으로 둔다(2026-07-18 피드백에서 스코어·급상승·급하락까지 우선
  // 통합했고, 2026-07-19 피드백으로 거래대금도 동일하게 맞춤) - 거래대금은
  // 아직 데이터 자체가 없어 토글을 눌러도 지금 당장은 결과가 안 바뀌지만,
  // 데이터가 붙었을 때 이 토글이 이미 자리 잡고 있도록 UI를 먼저 통일해둔다.
  const showWatchlistOnlyToggle = isAuthenticated
  const effectiveWatchlistOnly = showWatchlistOnlyToggle && watchlistOnly

  const rankingQuery = useMarketRankingQuery(
    sortKey === 'losers' ? 'losers' : 'gainers',
    10,
    isRealMode,
    effectiveWatchlistOnly,
  )
  const scoreQuery = useDashboardScoresQuery(effectiveWatchlistOnly, 10)
  const scoreStockCodes = isScoreMode ? (scoreQuery.data ?? []).map((item) => item.stockCode) : []
  // WebSocket 실시간 브로드캐스트는 장중에만 오므로 소켓만 쓰면 장마감엔
  // 현재가/등락률이 전부 "-"로 보인다(AppSidePanel에서 이미 한 번 겪은
  // 문제와 동일 - 2026-07-16). REST 폴링을 베이스라인으로 깔고 실시간
  // 푸시가 오면 그걸 우선시하는 이중 소스 패턴을 여기도 동일하게 적용.
  const scoreSocketPrices = useStockPriceSocket(scoreStockCodes)
  const scoreRestPrices = useStockPricesQuery(scoreStockCodes)

  const displayRows: DisplayRow[] = isScoreMode
    ? [...(scoreQuery.data ?? [])]
        .sort((a, b) => (b.compositeScore ?? -Infinity) - (a.compositeScore ?? -Infinity))
        .map((item) => {
          const livePrice = scoreSocketPrices[item.stockCode] ?? scoreRestPrices[item.stockCode]
          return {
            stockCode: item.stockCode,
            stockName: item.stockName,
            sector: item.sector,
            logoUrl: buildStockLogoUrl(item.stockCode),
            price: livePrice?.currentPrice ?? null,
            changeRate: livePrice?.changeRate ?? null,
            compositeScore: item.compositeScore,
            grade: item.grade,
          }
        })
    : isRealMode
      ? (rankingQuery.data ?? []).map((row) => ({
          stockCode: row.stockCode,
          stockName: row.stockName,
          sector: row.sector,
          logoUrl: buildStockLogoUrl(row.stockCode),
          price: row.currentPrice,
          changeRate: row.changeRate,
        }))
      : []

  const isLoading = (isRealMode && rankingQuery.isLoading) || (isScoreMode && scoreQuery.isLoading)

  return (
    <section className="rounded-2xl border border-gray-100 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-base font-semibold text-gray-900">실시간 랭킹</h2>
        <div className="relative flex items-center gap-2">
          {/* 별도 "관심종목 랭킹"/"/dashboard" 페이지를 새로 만드는 대신,
              스코어/급상승/급하락/거래대금 전부에 "관심종목만 보기" 토글을
              공통으로 붙였다(2026-07-18/19 피드백). 텍스트 버튼 대신
              하트 아이콘 온오프 토글로 바꾸고(2026-07-20 피드백), 종목별
              관심종목 하트와 동일한 색상 패턴(빨강=on)을 재사용해 같은
              화면 안에서 하트 색상 규칙이 하나로 통일되게 한다. */}
          {scope !== 'overseas' && showWatchlistOnlyToggle && (
            <button
              type="button"
              onClick={() => setWatchlistOnly(!watchlistOnly)}
              aria-pressed={watchlistOnly}
              aria-label={watchlistOnly ? '관심종목만 보기 해제' : '관심종목만 보기'}
              className={`flex h-7 w-7 items-center justify-center rounded-lg border transition ${
                watchlistOnly ? 'border-red-200 bg-red-50 hover:bg-red-100' : 'border-gray-200 hover:bg-gray-50'
              }`}
            >
              <svg
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill={watchlistOnly ? '#dc2626' : 'none'}
                stroke={watchlistOnly ? '#dc2626' : '#c6c6c6'}
                strokeWidth="2"
              >
                <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z" />
              </svg>
            </button>
          )}
          <select
            value={period}
            onChange={(event) => setPeriod(event.target.value as Period)}
            className="appearance-none rounded-lg border border-gray-200 bg-white py-1.5 pl-2.5 pr-7 text-xs font-medium text-gray-600 transition outline-none hover:bg-gray-50 focus:border-gray-400"
          >
            {PERIOD_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <svg
            className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2"
            width="10"
            height="10"
            viewBox="0 0 24 24"
            fill="none"
            stroke="#9ca3af"
            strokeWidth="2.5"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </div>
      </div>

      <div className="mb-2.5 flex flex-wrap items-center justify-between gap-2">
        <div className="flex rounded-xl bg-gray-100 p-1">
          {SCOPE_OPTIONS.map((option) => (
            <button
              key={option.key}
              type="button"
              onClick={() => setScope(option.key)}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                scope === option.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
        {scope !== 'overseas' && (
          <div className="flex rounded-xl bg-gray-100 p-1">
            {SORT_OPTIONS.map((option) => (
              <button
                key={option.key}
                type="button"
                onClick={() => setSortKey(option.key)}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                  sortKey === option.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {scope === 'overseas' ? (
        <p className="py-10 text-center text-sm text-gray-400">해외 주식은 아직 지원하지 않아요.</p>
      ) : (
        <>
          <p className="mb-2 text-xs text-gray-400">
            {period !== '실시간' && '기간별 랭킹은 아직 준비 중이라 실시간 기준으로 보여드려요 · '}
            {isScoreMode &&
              (effectiveWatchlistOnly
                ? '관심종목 중 종합점수가 높은 순입니다'
                : '전 상장종목 중 종합점수가 높은 순입니다')}
            {isRealMode &&
              (effectiveWatchlistOnly
                ? '관심종목만 실제 등락률로 정렬했습니다 · 장중에만 갱신됩니다'
                : '실제 등락률로 정렬한 전종목 랭킹입니다 · 장중에만 갱신됩니다')}
            {isAmountMode && '거래대금 랭킹은 아직 준비 중이에요'}
          </p>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left">
              <thead>
                <tr className="border-b border-gray-100 text-xs font-medium text-gray-400">
                  <th className="w-14 pb-2">순위</th>
                  <th className="pb-2">종목</th>
                  <th className="pb-2 text-right">현재가</th>
                  <th className="pb-2 text-right">등락률</th>
                  <th className="pb-2 text-right">스코어</th>
                  <th className="pb-2 text-right">거래대금</th>
                  <th className="pb-2 pl-4 text-left">산업</th>
                </tr>
              </thead>
              <tbody>
                {isLoading && (
                  <tr>
                    <td colSpan={7} className="py-6 text-center text-sm text-gray-400">
                      불러오는 중...
                    </td>
                  </tr>
                )}
                {isAmountMode && (
                  <tr>
                    <td colSpan={7} className="py-6 text-center text-sm text-gray-400">
                      거래대금 랭킹은 아직 준비 중이에요.
                    </td>
                  </tr>
                )}
                {!isLoading && !isAmountMode && displayRows.length === 0 && (
                  <tr>
                    <td colSpan={7} className="py-6 text-center text-sm text-gray-400">
                      {isScoreMode &&
                        effectiveWatchlistOnly &&
                        '관심 종목이 없거나 아직 계산된 스코어가 없습니다.'}
                      {isScoreMode && !effectiveWatchlistOnly && '아직 계산된 스코어가 없습니다.'}
                      {isRealMode &&
                        effectiveWatchlistOnly &&
                        '관심 종목이 없거나, 관심종목 중 지금 등락률 데이터가 있는 종목이 없습니다.'}
                      {isRealMode &&
                        !effectiveWatchlistOnly &&
                        '장이 열려 있지 않거나 아직 데이터가 준비되지 않았습니다.'}
                    </td>
                  </tr>
                )}
                {!isAmountMode &&
                  displayRows.map((row, index) => (
                    <RankingRow
                      key={row.stockCode}
                      row={row}
                      index={index}
                      isWatched={watchlistCodes.has(row.stockCode)}
                      onToggleWatch={onToggleWatch}
                    />
                  ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}
