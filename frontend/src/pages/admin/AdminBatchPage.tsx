import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { restartBatchExecution } from '../../api/admin'
import { BatchExecutionRow } from '../../components/admin/BatchExecutionRow'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { useBatchExecutions } from '../../hooks/queries/useBatchExecutions'
import { queryKeys } from '../../hooks/queryKeys'
import type { BatchExecution } from '../../types/admin'
import { describeAdminError } from '../../utils/adminError'
import { hasRunning, jobLabel } from '../../utils/batchExecution'

type Notice = { kind: 'done' | 'error'; text: string }

export function AdminBatchPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  // 시작 응답의 "실행 상태 보기" 링크(?execution=ID)로 들어오면 그 실행을 펼쳐서 보여준다
  const highlightedId = Number(searchParams.get('execution')) || null
  const { data, isLoading, isError, error, isFetching, refetch } = useBatchExecutions()
  const [openIds, setOpenIds] = useState<Set<number>>(() => new Set(highlightedId ? [highlightedId] : []))
  const [restartTarget, setRestartTarget] = useState<BatchExecution | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)

  const restart = useMutation({
    mutationFn: (executionId: number) => restartBatchExecution(executionId),
    onSuccess: (message) => {
      setNotice({ kind: 'done', text: message })
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminBatchExecutionsAll })
    },
    onError: (restartError) => setNotice({ kind: 'error', text: describeAdminError(restartError) }),
  })

  const executions = data ?? []
  const running = hasRunning(executions)

  function toggle(executionId: number) {
    setOpenIds((previous) => {
      const next = new Set(previous)
      if (next.has(executionId)) next.delete(executionId)
      else next.add(executionId)
      return next
    })
  }

  function confirmRestart() {
    if (!restartTarget) return
    setNotice(null)
    restart.mutate(restartTarget.executionId)
    setRestartTarget(null)
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-base font-bold text-gray-900">배치 실행</h2>
        <p className="text-sm text-gray-500">
          스코어 재구성과 백테스트처럼 오래 걸리는 작업의 최근 실행이에요. 시각은 서버(한국 시간) 기준이고, 실행 중인
          작업이 있으면 5초마다 자동으로 갱신해요.
        </p>
      </div>

      <div className="flex items-center justify-between gap-3">
        <p className="text-xs text-gray-500">{running ? '실행 중인 작업이 있어요' : '실행 중인 작업이 없어요'}</p>
        <button
          type="button"
          onClick={() => void refetch()}
          disabled={isFetching}
          className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isFetching ? '불러오는 중…' : '새로고침'}
        </button>
      </div>

      <div aria-live="polite" className="empty:hidden">
        {notice?.kind === 'done' && (
          <p className="rounded-lg bg-gray-50 px-3 py-2 text-sm text-gray-700">{notice.text}</p>
        )}
        {notice?.kind === 'error' && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {notice.text}
          </p>
        )}
      </div>

      {isLoading && <p className="text-sm text-gray-500">불러오는 중…</p>}
      {isError && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {describeAdminError(error)}
        </p>
      )}
      {!isLoading && !isError && executions.length === 0 && (
        <p className="rounded-2xl border border-gray-100 bg-white p-5 text-sm text-gray-500">
          아직 실행 이력이 없어요.
        </p>
      )}

      <div className="flex flex-col gap-2">
        {executions.map((execution) => (
          <BatchExecutionRow
            key={execution.executionId}
            execution={execution}
            open={openIds.has(execution.executionId)}
            highlighted={execution.executionId === highlightedId}
            restarting={restart.isPending && restart.variables === execution.executionId}
            onToggle={() => toggle(execution.executionId)}
            onRestart={() => setRestartTarget(execution)}
          />
        ))}
      </div>

      <ConfirmDialog
        open={restartTarget !== null}
        title="이어서 실행할까요?"
        description={
          restartTarget
            ? `${jobLabel(restartTarget.jobName)} #${restartTarget.executionId}을 같은 조건으로 이어서 실행해요. 이미 끝난 단계는 건너뛰고 끊긴 지점부터 계속해요.`
            : ''
        }
        confirmLabel="이어서 실행"
        onConfirm={confirmRestart}
        onCancel={() => setRestartTarget(null)}
      />
    </div>
  )
}
