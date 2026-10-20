import { useState, type ReactNode } from 'react'
import { describeAdminError } from '../../utils/adminError'
import { ConfirmDialog } from '../common/ConfirmDialog'

interface AdminActionCardProps<T> {
  title: string
  description: string
  buttonLabel: string
  // danger: 데이터를 지우는 것처럼 되돌릴 수 없는 작업 - 버튼과 확인 창을 빨간색으로 구분한다
  tone?: 'default' | 'danger'
  // 있으면 실행 전에 확인 창을 거친다(무겁거나 되돌릴 수 없는 작업)
  confirm?: { title: string; description: string }
  disabled?: boolean
  run: () => Promise<T>
  // 결과가 문자열이 아니면 화면에 보일 모양을 직접 그린다
  renderResult?: (result: T) => ReactNode
  // 실행에 필요한 입력 필드
  children?: ReactNode
}

type Phase =
  | { kind: 'idle' }
  | { kind: 'running' }
  | { kind: 'done'; value: unknown; at: Date }
  | { kind: 'error'; message: string; at: Date }

function formatTime(date: Date): string {
  return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function AdminActionCard<T>({
  title,
  description,
  buttonLabel,
  tone = 'default',
  confirm,
  disabled = false,
  run,
  renderResult,
  children,
}: AdminActionCardProps<T>) {
  const [phase, setPhase] = useState<Phase>({ kind: 'idle' })
  const [confirmOpen, setConfirmOpen] = useState(false)
  const running = phase.kind === 'running'

  async function execute() {
    setConfirmOpen(false)
    setPhase({ kind: 'running' })
    try {
      const value = await run()
      setPhase({ kind: 'done', value, at: new Date() })
    } catch (error) {
      setPhase({ kind: 'error', message: describeAdminError(error), at: new Date() })
    }
  }

  function handleClick() {
    if (running || disabled) return
    if (confirm) setConfirmOpen(true)
    else void execute()
  }

  const buttonClass =
    tone === 'danger'
      ? 'border border-red-200 text-red-600 hover:bg-red-50'
      : 'bg-gray-900 text-white hover:bg-gray-800'

  return (
    <section className="rounded-2xl border border-gray-100 bg-white p-5">
      <h3 className="mb-1 text-sm font-bold text-gray-900">{title}</h3>
      <p className="mb-3 max-w-prose text-sm text-gray-500">{description}</p>
      {children && <div className="mb-3 flex flex-col gap-3">{children}</div>}
      <button
        type="button"
        onClick={handleClick}
        disabled={running || disabled}
        className={`rounded-lg px-4 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${buttonClass}`}
      >
        {running ? '실행 중…' : buttonLabel}
      </button>

      <div aria-live="polite" className="mt-3 empty:hidden">
        {phase.kind === 'done' && (
          <div className="rounded-lg bg-gray-50 px-3 py-2 text-sm text-gray-700">
            <p className="mb-1 text-xs text-gray-400">{formatTime(phase.at)} 완료</p>
            {renderResult ? renderResult(phase.value as T) : <p className="break-words">{String(phase.value)}</p>}
          </div>
        )}
        {phase.kind === 'error' && (
          <div role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            <p className="mb-1 text-xs text-red-400">{formatTime(phase.at)} 실패</p>
            <p className="break-words">{phase.message}</p>
          </div>
        )}
      </div>

      {confirm && (
        <ConfirmDialog
          open={confirmOpen}
          tone={tone}
          title={confirm.title}
          description={confirm.description}
          confirmLabel={buttonLabel}
          onConfirm={() => void execute()}
          onCancel={() => setConfirmOpen(false)}
        />
      )}
    </section>
  )
}
