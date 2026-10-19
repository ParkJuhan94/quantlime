interface ConfirmDialogProps {
  open: boolean
  title: string
  description: string
  confirmLabel: string
  // danger: 되돌릴 수 없는 삭제처럼 실수의 대가가 큰 동작 - 확인 버튼을 빨간색으로 구분한다
  tone?: 'default' | 'danger'
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = 'default',
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) return null
  const confirmClass = tone === 'danger' ? 'bg-red-600 hover:bg-red-700' : 'bg-gray-900 hover:bg-gray-800'

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/35 px-4" onClick={onCancel}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-2xl"
      >
        <p id="confirm-dialog-title" className="mb-1 text-sm font-semibold text-gray-900">
          {title}
        </p>
        <p className="mb-4 break-words text-sm text-gray-500">{description}</p>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-lg border border-gray-200 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50"
          >
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className={`flex-1 rounded-lg py-2.5 text-sm font-semibold text-white ${confirmClass}`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
