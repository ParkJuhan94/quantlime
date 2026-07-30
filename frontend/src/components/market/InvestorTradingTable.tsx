import type { InvestorTradingResponse } from '../../types/market'
import { changeRateColorClass, formatKrwAmount } from '../../utils/priceFormat'

const COLUMNS: { key: keyof InvestorTradingResponse; label: string }[] = [
  { key: 'individualNetBuyAmount', label: '개인' },
  { key: 'foreignerNetBuyAmount', label: '외국인' },
  { key: 'institutionNetBuyAmount', label: '기관' },
  { key: 'financialInvestmentNetBuyAmount', label: '금융투자' },
  { key: 'insuranceNetBuyAmount', label: '보험' },
  { key: 'trustNetBuyAmount', label: '투신' },
  { key: 'privateEquityFundNetBuyAmount', label: '사모' },
  { key: 'bankNetBuyAmount', label: '은행' },
  { key: 'otherFinancialInstitutionNetBuyAmount', label: '기타금융' },
  { key: 'pensionFundNetBuyAmount', label: '연기금' },
  { key: 'otherCorporationNetBuyAmount', label: '기타법인' },
]

// 11주체 전체 표(키움 HTS 참고). 컬럼이 많아 페이지 본문이 가로 스크롤
// 되지 않도록 표 자체를 overflow-x-auto 컨테이너에 담고, 일자 컬럼만
// sticky로 고정한다.
export function InvestorTradingTable({ data }: { data: InvestorTradingResponse[] }) {
  if (data.length === 0) return null
  const rows = [...data].reverse() // 최신 날짜가 위로 오게(HTS 관례)

  return (
    <div className="mt-2 max-h-64 overflow-auto rounded-lg border border-gray-200">
      <table className="min-w-max text-xs">
        <thead>
          <tr className="border-b border-gray-200 bg-gray-50">
            <th className="sticky left-0 z-10 bg-gray-50 px-2 py-1.5 text-left font-medium text-gray-500">일자</th>
            {COLUMNS.map((col) => (
              <th key={col.key} className="whitespace-nowrap px-2 py-1.5 text-right font-medium text-gray-500">
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.baseDate} className="border-b border-gray-100 last:border-0">
              <td className="sticky left-0 z-10 whitespace-nowrap bg-white px-2 py-1.5 text-gray-500">
                {row.baseDate}
              </td>
              {COLUMNS.map((col) => (
                <td
                  key={col.key}
                  className={`whitespace-nowrap px-2 py-1.5 text-right ${changeRateColorClass(row[col.key] as number)}`}
                >
                  {formatKrwAmount(row[col.key] as number)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
