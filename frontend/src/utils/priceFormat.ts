export function formatPrice(price: number | null | undefined): string {
  // 국내 주식은 원 단위 정수로만 거래되므로 소수점은 항상 버린다.
  return price != null
    ? `${Math.round(price).toLocaleString('ko-KR', { maximumFractionDigits: 0 })}원`
    : '-'
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
