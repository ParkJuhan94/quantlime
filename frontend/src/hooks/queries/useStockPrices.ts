import { useQueries } from '@tanstack/react-query'
import { getCurrentPrice } from '../../api/stocks'
import { queryKeys } from '../queryKeys'
import type { PriceBroadcastMessage } from '../../types/realtime'

/**
 * REST 폴링으로 여러 종목의 현재가를 채운다. WebSocket 실시간 브로드캐스트
 * (PriceBroadcastScheduler)는 장중에만 동작하고, "최근 본" 종목은 애초에
 * 그 폴링 대상(WatchlistedStockCodeCache 기준)에도 없어 구독만으로는
 * 시세가 절대 오지 않는다 - REST로 직접 채운다.
 *
 * 관심종목은 WebSocket 구독(useStockPriceSocket)과 함께 이 훅도 같이 써서
 * "실시간 푸시가 있으면 그걸 우선, 없으면(장마감 등) REST 값으로 폴백"
 * 하는 이중 소스 패턴을 쓴다(StockDetailPage의 livePrice ?? restPrice와
 * 동일한 패턴) - 장마감에도 최근 종가는 보여야 하므로.
 */
export function useStockPricesQuery(stockCodes: string[]): Record<string, PriceBroadcastMessage> {
  const results = useQueries({
    queries: stockCodes.map((stockCode) => ({
      queryKey: queryKeys.stockPrice(stockCode),
      queryFn: () => getCurrentPrice(stockCode),
      refetchInterval: 5_000,
    })),
  })

  const prices: Record<string, PriceBroadcastMessage> = {}
  results.forEach((result) => {
    const data = result.data
    if (!data) return
    prices[data.stockCode] = {
      stockCode: data.stockCode,
      currentPrice: data.price,
      changeRate: data.changeRate,
      timestamp: data.timestamp ?? '',
    }
  })
  return prices
}
