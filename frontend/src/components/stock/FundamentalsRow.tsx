import type { StockFundamentalsResponse } from '../../types/stock'

function formatMarketCap(value: number | null): string | null {
  if (value == null) return null
  return `${(value / 1_000_000_000_000).toLocaleString('ko-KR', { maximumFractionDigits: 1 })}조`
}

function formatRatio(value: number | null, suffix = ''): string | null {
  if (value == null) return null
  return `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}${suffix}`
}

// 한 줄 나열(`라벨 값 · 라벨 값 · ...`)로 압축한다(frontend/CLAUDE.md
// "부가 정보는 카드로 만들지 말 것" 컨벤션 - 예전엔 박스 6개였음, 2026-07-17
// 이력 참고). flex-wrap이라 좁은 화면에서도 줄바꿈만 될 뿐 잘리지 않는다.
// 값 없는 항목(네이버 비공식 API 파싱 실패 가능)은 조용히 숨긴다.
export function FundamentalsRow({ fundamentals }: { fundamentals: StockFundamentalsResponse | undefined }) {
  if (!fundamentals) return null

  const items: { label: string; value: string | null }[] = [
    { label: '시총', value: formatMarketCap(fundamentals.marketCap) },
    { label: 'PER', value: formatRatio(fundamentals.per) },
    { label: 'Fwd PER', value: formatRatio(fundamentals.forwardPer) },
    { label: 'PBR', value: formatRatio(fundamentals.pbr) },
    { label: 'PSR', value: formatRatio(fundamentals.psr) },
    { label: '부채비율', value: formatRatio(fundamentals.debtRatio, '%') },
  ].filter((item) => item.value != null)

  if (items.length === 0) return null

  return (
    <div className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-gray-500">
      {items.map((item, index) => (
        <span key={item.label} className="whitespace-nowrap">
          {index > 0 && <span className="mr-1.5 text-gray-300">·</span>}
          {item.label} <span className="font-semibold text-gray-900">{item.value}</span>
        </span>
      ))}
    </div>
  )
}
