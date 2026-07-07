import { useEffect, useState } from 'react'
import { subscribeStockPrice } from '../realtime/stompClient'
import type { PriceBroadcastMessage } from '../types/realtime'

/**
 * 주어진 종목 코드들의 실시간 시세를 구독한다. 장중이 아니면 백엔드가
 * 아예 메시지를 보내지 않으므로(에러 아님) 해당 종목은 그냥 값이
 * 없는 상태로 남는다 - 호출 측에서 대시(-)로 표시.
 */
export function useStockPriceSocket(stockCodes: string[]): Record<string, PriceBroadcastMessage> {
  const [prices, setPrices] = useState<Record<string, PriceBroadcastMessage>>({})

  // 배열은 매 렌더마다 새 참조일 수 있어, 내용이 같으면 effect가 다시
  // 돌지 않도록 정렬된 문자열 키로 변환해 의존성에 사용한다.
  const codesKey = [...stockCodes].sort().join(',')

  useEffect(() => {
    const codes = codesKey ? codesKey.split(',') : []
    const unsubscribers = codes.map((stockCode) =>
      subscribeStockPrice(stockCode, (message) => {
        setPrices((prev) => ({ ...prev, [stockCode]: message }))
      }),
    )

    return () => {
      unsubscribers.forEach((unsubscribe) => unsubscribe())
    }
    // codesKey가 stockCodes의 내용을 정확히 대표하므로 이것만 의존성으로 둔다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [codesKey])

  return prices
}
