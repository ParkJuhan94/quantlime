import { useQuery } from '@tanstack/react-query'
import { getBacktest } from '../../api/backtest'
import { queryKeys } from '../queryKeys'

export function useBacktestQuery(stockCode: string) {
  return useQuery({
    queryKey: queryKeys.backtest(stockCode),
    queryFn: () => getBacktest(stockCode),
    // 백테스트는 수동/배치 트리거로만 갱신되고 화면에서 매번 새로 계산되지
    // 않으므로 스코어(1분)보다 훨씬 길게 캐싱한다.
    staleTime: 60 * 60 * 1000,
    // 백테스트 미계산(BT_000)은 404로 오는 정상 상태라 재시도가 무의미하다
    // (useStockScoreQuery와 동일한 이유).
    retry: false,
  })
}
