import { useQuery } from '@tanstack/react-query'
import { getChart, getCurrentPrice, getFundamentals, getScore, getStock } from '../../api/stocks'
import { queryKeys } from '../queryKeys'

export function useStockDetailQuery(stockCode: string) {
  return useQuery({
    queryKey: queryKeys.stockDetail(stockCode),
    queryFn: () => getStock(stockCode),
    staleTime: 5 * 60 * 1000,
  })
}

export function useStockPriceQuery(stockCode: string) {
  return useQuery({
    queryKey: queryKeys.stockPrice(stockCode),
    queryFn: () => getCurrentPrice(stockCode),
  })
}

export function useStockChartQuery(stockCode: string, days: number) {
  return useQuery({
    queryKey: queryKeys.stockChart(stockCode, days),
    queryFn: () => getChart(stockCode, days),
    staleTime: 5 * 60 * 1000,
  })
}

// 재무 데이터는 분기 단위로만 바뀌니 서버 캐시(1시간)에 맞춰 길게 둔다.
export function useStockFundamentalsQuery(stockCode: string) {
  return useQuery({
    queryKey: queryKeys.stockFundamentals(stockCode),
    queryFn: () => getFundamentals(stockCode),
    staleTime: 60 * 60 * 1000,
  })
}

// enabled=false(비구독)면 요청 자체를 보내지 않는다 - 백엔드가 이제
// 403으로 막긴 하지만, 프론트가 굳이 막힐 요청을 보내 네트워크 탭에
// 흔적을 남길 이유가 없다(PremiumGate 참고).
export function useStockScoreQuery(stockCode: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.stockScore(stockCode),
    queryFn: () => getScore(stockCode),
    staleTime: 60 * 1000,
    // 스코어 미계산(SC_000)은 404로 오는 정상 상태라 재시도가 무의미하다.
    retry: false,
    enabled,
  })
}
