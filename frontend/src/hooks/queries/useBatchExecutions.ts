import { useQuery } from '@tanstack/react-query'
import { fetchBatchExecution, fetchBatchExecutions } from '../../api/admin'
import { hasRunning, isRunning } from '../../utils/batchExecution'
import { queryKeys } from '../queryKeys'

const POLL_INTERVAL_MS = 5_000

// 실행 중인 항목이 하나라도 있을 때만 5초마다 다시 불러오고, 모두 끝나면 멈춘다
export function useBatchExecutions(limit = 30) {
  return useQuery({
    queryKey: queryKeys.adminBatchExecutions(limit),
    queryFn: () => fetchBatchExecutions(limit),
    refetchInterval: (query) => (hasRunning(query.state.data ?? []) ? POLL_INTERVAL_MS : false),
  })
}

// 펼친 행의 Step 상세. 목록에는 Step이 없어서 펼칠 때만 단건으로 가져오고, 그 실행이 진행 중이면 같이 갱신한다
export function useBatchExecutionDetail(executionId: number, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.adminBatchExecution(executionId),
    queryFn: () => fetchBatchExecution(executionId),
    enabled,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status !== undefined && isRunning(status) ? POLL_INTERVAL_MS : false
    },
  })
}
