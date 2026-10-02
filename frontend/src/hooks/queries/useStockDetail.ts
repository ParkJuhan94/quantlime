import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import {
  getChart,
  getCurrentPrice,
  getFundamentals,
  getMinuteChart,
  getOrderbook,
  getPriceLimit,
  getScore,
  getStock,
  getStockWarnings,
  getTrades,
} from '../../api/stocks'
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

// 호가/체결은 서버가 2초 캐싱하므로 프론트도 3초 폴링(탭이 백그라운드면 React Query가
// 기본으로 멈춘다). 실패해도 이전 값을 유지해 화면이 깜빡이지 않게 retry는 1회만.
export function useStockOrderbookQuery(stockCode: string) {
  return useQuery({
    queryKey: queryKeys.stockOrderbook(stockCode),
    queryFn: () => getOrderbook(stockCode),
    refetchInterval: 3_000,
    retry: 1,
  })
}

export function useStockTradesQuery(stockCode: string) {
  return useQuery({
    queryKey: queryKeys.stockTrades(stockCode),
    queryFn: () => getTrades(stockCode),
    refetchInterval: 3_000,
    retry: 1,
  })
}

export function useStockPriceLimitQuery(stockCode: string) {
  return useQuery({
    queryKey: queryKeys.stockPriceLimit(stockCode),
    queryFn: () => getPriceLimit(stockCode),
    staleTime: 60 * 1000,
    retry: 1,
  })
}

export function useStockWarningsQuery(stockCode: string) {
  return useQuery({
    queryKey: queryKeys.stockWarnings(stockCode),
    queryFn: () => getStockWarnings(stockCode),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  })
}

// 분봉은 호출당 최대 200개(약 3.3시간)라 nextBefore 커서로 과거 방향 페이지를 이어 받는다.
// 1분마다 다시 받아 최신 봉을 반영하고(서버 캐시 15초), 분봉 모드가 아니면 요청 자체를 보내지 않는다.
export function useStockMinuteChartQuery(stockCode: string, enabled: boolean) {
  return useInfiniteQuery({
    queryKey: queryKeys.stockMinuteChart(stockCode),
    queryFn: ({ pageParam }) => getMinuteChart(stockCode, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextBefore ?? undefined,
    enabled,
    refetchInterval: enabled ? 60_000 : false,
    staleTime: 15_000,
    retry: 1,
  })
}
