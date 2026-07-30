import { useState } from 'react'
import { useInvestorTradingQuery } from '../../hooks/queries/useInvestorTrading'
import { InvestorTradingTable } from './InvestorTradingTable'
import { changeRateColorClass, formatKrwAmount } from '../../utils/priceFormat'
import type { InvestorTradingInterval } from '../../types/market'

const INTERVAL_OPTIONS: { key: InvestorTradingInterval; label: string }[] = [
  { key: 'weekly', label: '주' },
  { key: 'monthly', label: '월' },
]

// 코스피/코스닥 상세 페이지 차트 카드 헤더에 들어가는 투자자별 순매수
// 요약 - 4대 주체(개인/외국인/기관/기타법인) 칩만 기본 노출하고, "자세히"를
// 눌러야 11주체 전체 표(InvestorTradingTable)가 펼쳐진다(레이아웃을
// 많이 차지하지 않기 위함, 2026-07-30). 이 컴포넌트는 justify-between
// flex 행의 아이템으로 쓰이는 걸 전제로, 펼침 표는 order-last + w-full로
// 같은 행에서 새 줄로 떨어지게 한다.
export function InvestorTradingSummary({ code }: { code: 'KOSPI' | 'KOSDAQ' }) {
  const [interval, setInterval] = useState<InvestorTradingInterval>('weekly')
  const [expanded, setExpanded] = useState(false)
  const { data } = useInvestorTradingQuery(code, interval)
  const latest = data?.[data.length - 1]

  if (!latest) return null

  const chips: { label: string; value: number }[] = [
    { label: '개인', value: latest.individualNetBuyAmount },
    { label: '외국인', value: latest.foreignerNetBuyAmount },
    { label: '기관', value: latest.institutionNetBuyAmount },
    { label: '기타법인', value: latest.otherCorporationNetBuyAmount },
  ]

  return (
    <>
      <div className="flex flex-wrap items-center gap-1.5 text-xs">
        {chips.map((chip) => (
          <div key={chip.label} className="rounded-lg border border-gray-200 bg-gray-50 px-2 py-1">
            <span className="text-gray-400">{chip.label}</span>{' '}
            <span className={`font-semibold ${changeRateColorClass(chip.value)}`}>{formatKrwAmount(chip.value)}</span>
          </div>
        ))}
        <div className="flex gap-0.5">
          {INTERVAL_OPTIONS.map((option) => (
            <button
              key={option.key}
              type="button"
              onClick={() => setInterval(option.key)}
              className={`rounded-md px-1.5 py-0.5 text-[10px] font-medium transition ${
                interval === option.key ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="text-gray-500 hover:underline"
        >
          {expanded ? '접기' : '자세히'}
        </button>
      </div>
      {expanded && (
        <div className="order-last w-full">
          <InvestorTradingTable data={data ?? []} />
        </div>
      )}
    </>
  )
}
