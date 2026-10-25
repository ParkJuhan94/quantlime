import { useState } from 'react'
import { useStockInvestorTradingQuery } from '../../hooks/queries/useStockDetail'
import {
  availableSummaryDays,
  formatHoldingRate,
  formatShares,
  sumNetBuyVolume,
  type InvestorGroupKey,
} from '../../utils/investorTrading'
import { changeRateColorClass } from '../../utils/priceFormat'

const GROUPS: { key: InvestorGroupKey; label: string }[] = [
  { key: 'individualNetBuyVolume', label: '개인' },
  { key: 'foreignerNetBuyVolume', label: '외국인' },
  { key: 'institutionNetBuyVolume', label: '기관' },
  { key: 'otherCorporationNetBuyVolume', label: '기타법인' },
]

const DEFAULT_SUMMARY_DAYS = 20

// 종목 상세의 투자자별 매매동향 - 국내 종목의 최근 120거래일 순매수 거래량(주, KRX·NXT 통합)을 일별 표(스크롤)와
// 5/20/60/120일 중 고른 기간의 누적 칩으로 보여준다. 코스피/코스닥 지수 상세의 매매동향(InvestorTradingSummary)은 거래대금(원)이라 단위가 다르다.
// 오늘 기록은 장중 잠정치라 개인·기타법인이 '-'이고 저녁에 확정된다. 해외 종목(서버가 빈 배열), 로딩/에러에서는 부가 정보라
// 영역 자체를 그리지 않는다.
export function StockInvestorTradingSection({ stockCode }: { stockCode: string }) {
  const { data } = useStockInvestorTradingQuery(stockCode)
  const [selectedDays, setSelectedDays] = useState(DEFAULT_SUMMARY_DAYS)

  if (!data || data.length === 0) return null

  // 선택한 기간이 이 종목의 확정 거래일 수보다 길면(신규 상장 등) 가장 긴 가능한 구간으로 내린다.
  const dayOptions = availableSummaryDays(data)
  const summaryDays = dayOptions.includes(selectedDays) ? selectedDays : dayOptions[dayOptions.length - 1]

  const rows = [...data].reverse() // 최신 날짜가 위로 오게(HTS 관례)
  const latestHoldingRate = rows.find((row) => row.foreignerHoldingRate != null)?.foreignerHoldingRate

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-gray-900">투자자별 매매동향</h2>
        <span className="text-xs text-gray-400">
          순매수 거래량(주, KRX·NXT 통합) · 외국인 보유율{' '}
          <span className="text-gray-600">{formatHoldingRate(latestHoldingRate)}</span>
        </span>
      </div>

      <div className="flex flex-wrap items-center gap-1.5 text-xs">
        <div className="flex gap-0.5" role="group" aria-label="누적 기간">
          {dayOptions.map((days) => (
            <button
              key={days}
              type="button"
              onClick={() => setSelectedDays(days)}
              className={`rounded-md px-2 py-0.5 text-[11px] font-medium transition ${
                summaryDays === days ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {days}일
            </button>
          ))}
        </div>
        <span className="text-gray-400">누적</span>
        {GROUPS.map((group) => {
          const sum = sumNetBuyVolume(data, group.key, summaryDays)
          return (
            <div key={group.key} className="rounded-lg border border-gray-200 bg-gray-50 px-2 py-1">
              <span className="text-gray-400">{group.label}</span>{' '}
              <span className={`font-semibold ${changeRateColorClass(sum)}`}>{formatShares(sum)}</span>
            </div>
          )
        })}
      </div>

      <div className="mt-3 max-h-64 overflow-auto rounded-lg border border-gray-200">
        <table className="w-full min-w-max text-xs">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50">
              <th className="sticky left-0 z-10 bg-gray-50 px-2 py-1.5 text-left font-medium text-gray-500">일자</th>
              {GROUPS.map((group) => (
                <th key={group.key} className="whitespace-nowrap px-2 py-1.5 text-right font-medium text-gray-500">
                  {group.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.tradeDate} className="border-b border-gray-100 last:border-0">
                <td className="sticky left-0 z-10 whitespace-nowrap bg-white px-2 py-1.5 text-gray-500">
                  {row.tradeDate}
                  {!row.confirmed && <span className="ml-1 text-[10px] text-gray-400">잠정</span>}
                </td>
                {GROUPS.map((group) => (
                  <td
                    key={group.key}
                    className={`whitespace-nowrap px-2 py-1.5 text-right ${changeRateColorClass(row[group.key])}`}
                  >
                    {formatShares(row[group.key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
