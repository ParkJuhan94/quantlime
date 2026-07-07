export function formatPrice(price: number | null | undefined): string {
  return price != null ? price.toLocaleString('ko-KR') : '-'
}

export function formatChangeRate(rate: number | null | undefined): string {
  if (rate == null) return '-'
  const sign = rate > 0 ? '+' : ''
  return `${sign}${rate.toFixed(2)}%`
}

// 국내 주식 관례: 상승은 빨간색, 하락은 파란색(미국식과 반대).
export function changeRateColorClass(rate: number | null | undefined): string {
  if (rate == null) return 'text-gray-400'
  if (rate > 0) return 'text-red-600'
  if (rate < 0) return 'text-blue-600'
  return 'text-gray-600'
}
