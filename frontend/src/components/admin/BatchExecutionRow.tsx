import { useEffect, useRef } from 'react'
import { useBatchExecutionDetail } from '../../hooks/queries/useBatchExecutions'
import type { BatchExecution } from '../../types/admin'
import {
  canRestart,
  formatDateTime,
  formatDuration,
  isRunning,
  jobLabel,
  statusLabel,
} from '../../utils/batchExecution'

interface BatchExecutionRowProps {
  execution: BatchExecution
  open: boolean
  // 시작 응답 링크로 들어온 실행 - 처음 한 번 화면 가운데로 스크롤한다
  highlighted: boolean
  // 같은 JobInstance에 더 새로운 실행이 없는 실패·중단만 true - 이미 이어서 실행한 실패에는 버튼을 보이지 않는다
  restartable: boolean
  restarting: boolean
  onToggle: () => void
  onRestart: () => void
}

function StatusBadge({ status }: { status: string }) {
  if (isRunning(status)) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-lg bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-900">
        <span className="relative flex h-2 w-2">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-gray-900 opacity-60" />
          <span className="relative inline-flex h-2 w-2 rounded-full bg-gray-900" />
        </span>
        {statusLabel(status)}
      </span>
    )
  }
  const tone = status === 'FAILED' ? 'bg-red-50 text-red-700' : 'bg-gray-100 text-gray-600'
  return <span className={`rounded-lg px-2 py-0.5 text-xs font-semibold ${tone}`}>{statusLabel(status)}</span>
}

function StepTable({ execution }: { execution: BatchExecution }) {
  if (execution.steps.length === 0) {
    return <p className="text-xs text-gray-500">기록된 Step이 없어요.</p>
  }
  return (
    <div className="max-h-72 overflow-auto">
      <table className="w-full min-w-[28rem] text-left text-xs">
        <thead className="text-gray-400">
          <tr>
            <th className="py-1 pr-3 font-semibold">Step</th>
            <th className="py-1 pr-3 font-semibold">상태</th>
            <th className="py-1 pr-3 text-right font-semibold">읽기</th>
            <th className="py-1 pr-3 text-right font-semibold">쓰기</th>
            <th className="py-1 text-right font-semibold">건너뜀</th>
          </tr>
        </thead>
        <tbody>
          {execution.steps.map((step) => (
            <tr key={step.name} className={step.status === 'FAILED' ? 'text-red-700' : 'text-gray-700'}>
              <td className="break-all py-1 pr-3">{step.name}</td>
              <td className="py-1 pr-3">{statusLabel(step.status)}</td>
              <td className="py-1 pr-3 text-right tabular-nums">{step.readCount}</td>
              <td className="py-1 pr-3 text-right tabular-nums">{step.writeCount}</td>
              <td className="py-1 text-right tabular-nums">{step.skipCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function BatchExecutionRow({
  execution,
  open,
  highlighted,
  restartable,
  restarting,
  onToggle,
  onRestart,
}: BatchExecutionRowProps) {
  const rowRef = useRef<HTMLDivElement>(null)
  const detail = useBatchExecutionDetail(execution.executionId, open)
  // 펼친 동안은 단건 조회 값이 목록보다 최신일 수 있다(실행 중인 항목은 같은 주기로 같이 갱신된다)
  const current = detail.data ?? execution
  const startedAt = execution.startTime ?? execution.createTime

  useEffect(() => {
    if (highlighted) rowRef.current?.scrollIntoView({ block: 'center' })
  }, [highlighted])

  return (
    <div ref={rowRef} className={`rounded-xl border border-gray-100 ${open ? 'bg-gray-50' : 'bg-white'}`}>
      <div className="flex items-center gap-2 p-3">
        <button
          type="button"
          aria-expanded={open}
          onClick={onToggle}
          className="flex min-w-0 flex-1 flex-wrap items-center gap-x-3 gap-y-1 text-left"
        >
          <StatusBadge status={current.status} />
          <span className="text-sm font-semibold text-gray-900">{jobLabel(execution.jobName)}</span>
          <span className="text-xs text-gray-400">#{execution.executionId}</span>
          <span className="ml-auto text-xs text-gray-500">
            {formatDateTime(startedAt)}
            <span className="ml-2 text-gray-400">{formatDuration(current.startTime, current.endTime)}</span>
          </span>
        </button>
        {restartable && canRestart(current.status) && (
          <button
            type="button"
            disabled={restarting}
            onClick={onRestart}
            className="shrink-0 rounded-lg bg-gray-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {restarting ? '시작 중…' : '이어서 실행'}
          </button>
        )}
      </div>

      {open && (
        <div className="flex flex-col gap-3 border-t border-gray-100 p-3">
          {detail.isLoading && <p className="text-xs text-gray-500">불러오는 중…</p>}
          {detail.isError && <p className="text-xs text-red-700">상세를 불러오지 못했어요.</p>}
          {detail.data && (
            <>
              <StepTable execution={detail.data} />
              {detail.data.status === 'FAILED' && detail.data.exitDescription && (
                <pre className="max-h-40 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-red-50 p-2 text-xs text-red-700">
                  {detail.data.exitDescription}
                </pre>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}
