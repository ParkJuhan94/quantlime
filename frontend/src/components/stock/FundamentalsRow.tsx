import type { StockFundamentalsResponse } from '../../types/stock'

function formatMarketCap(value: number | null): string | null {
  if (value == null) return null
  return `${(value / 1_000_000_000_000).toLocaleString('ko-KR', { maximumFractionDigits: 1 })}조`
}

function formatRatio(value: number | null, suffix = ''): string | null {
  if (value == null) return null
  return `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}${suffix}`
}

// 시총/PER/포워드PER/PBR/PSR/부채비율을 무채색 박스 6개(2줄×3)로 나열한다.
// 박스 안은 라벨+값을 한 줄로 압축(2026-07-17 - 이전엔 라벨/값이 두 줄로
// 나뉘어 있었는데, 한 줄로 합치면서 6개를 1줄에 다 넣기엔 박스가 너무
// 좁아져 3열×2줄로 되돌림). 값이 없는 항목(네이버 비공식 API 특성상
// 파싱 실패 가능)은 조용히 숨긴다 - 남은 항목들로 그리드를 다시 채운다.
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
    <div className="grid grid-cols-3 gap-1.5">
      {items.map((item) => (
        <div
          key={item.label}
          className="truncate rounded-lg border border-gray-200 bg-gray-50 px-2 py-1 text-xs"
        >
          <span className="text-gray-400">{item.label}</span>{' '}
          <span className="font-semibold text-gray-900">{item.value}</span>
        </div>
      ))}
    </div>
  )
}
