import { useQuery } from '@tanstack/react-query'
import {
  getBitcoinChart,
  getExchangeRateChart,
  getIndexChart,
  getIndexMinuteChart,
  getMarketIndices,
} from '../../api/market'
import { queryKeys } from '../queryKeys'
import type { ChartIndexCode } from '../../types/market'

// 백엔드 MarketIndexCache가 5초 TTL로 캐싱하므로 그것보다 짧게 돌 이유가
// 없다 - 딱 그 주기로 맞춰서 "새 값이 왔을 때 최대한 빨리 반영"되게 한다
// (예전엔 60초→20초→8초→5초로 단계적으로 단축, 2026-07-17 - 8초도
// 느리다는 피드백. 더 낮추려면 네이버/TradingView 비공식 스크래핑
// 차단 위험과 저울질 필요, MarketIndexCache 주석 참고).
export function useMarketIndicesQuery() {
  return useQuery({
    queryKey: queryKeys.marketIndices,
    queryFn: getMarketIndices,
    staleTime: 5_000,
    refetchInterval: 5_000,
  })
}

// 일봉 차트라 장중에도 분 단위로 바뀌지 않는다 - 서버 캐시(60초)보다
// 살짝 더 긴 주기로 재조회한다.
export function useIndexChartQuery(code: ChartIndexCode, enabled = true) {
  return useQuery({
    queryKey: queryKeys.indexChart(code),
    queryFn: () => getIndexChart(code),
    staleTime: 90_000,
    refetchInterval: 90_000,
    enabled,
  })
}

// 당일 분봉이라 장중엔 계속 늘어난다 - 서버 캐시(20초)보다 살짝 더 긴
// 주기로 재조회해 홈 카드의 "진행중" 라인차트가 자연스럽게 갱신되게 한다.
export function useIndexMinuteChartQuery(code: 'KOSPI' | 'KOSDAQ', enabled = true) {
  return useQuery({
    queryKey: queryKeys.indexMinuteChart(code),
    queryFn: () => getIndexMinuteChart(code),
    staleTime: 30_000,
    refetchInterval: 30_000,
    enabled,
  })
}

// 비트코인은 30분봉이라 인트라데이 코스피/코스닥만큼 자주 안 바뀐다 -
// 서버 캐시(20초)보다 살짝 더 긴 주기로 재조회.
export function useBitcoinChartQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.bitcoinChart,
    queryFn: getBitcoinChart,
    staleTime: 30_000,
    refetchInterval: 30_000,
    enabled,
  })
}

// 환율도 해외지수와 동일하게 일봉 이력만 제공된다(분봉 없음).
export function useExchangeRateChartQuery(enabled = true) {
  return useQuery({
    queryKey: queryKeys.exchangeRateChart,
    queryFn: getExchangeRateChart,
    staleTime: 90_000,
    refetchInterval: 90_000,
    enabled,
  })
}
