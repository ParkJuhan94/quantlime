import { useEffect, useState } from 'react'

interface GroupNameDialogProps {
  open: boolean
  title: string
  initialName?: string
  submitLabel: string
  onSubmit: (name: string) => void
  onClose: () => void
}

// "그룹 추가하기"(생성)와 그룹 이름 변경(수정) 둘 다 이 컴포넌트 하나로
// 처리한다 - 입력창 하나 + 닫기/제출 버튼이라는 동일한 구조라 굳이
// 나눌 이유가 없다.
export function GroupNameDialog({
  open,
  title,
  initialName = '',
  submitLabel,
  onSubmit,
  onClose,
}: GroupNameDialogProps) {
  const [name, setName] = useState(initialName)

  useEffect(() => {
    if (open) setName(initialName)
  }, [open, initialName])

  useEffect(() => {
    if (!open) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  function handleSubmit() {
    const trimmed = name.trim()
    if (!trimmed) return
    onSubmit(trimmed)
  }

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/35" onClick={onClose}>
      <div
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-xs rounded-2xl bg-white p-5 shadow-2xl"
      >
        <p className="mb-3 text-sm font-semibold text-gray-900">{title}</p>
        <input
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && handleSubmit()}
          placeholder="그룹 이름 입력"
          autoFocus
          className="mb-4 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-900 outline-none focus:border-gray-400"
        />
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-lg border border-gray-200 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-50"
          >
            닫기
          </button>
          <button
            type="button"
            disabled={!name.trim()}
            onClick={handleSubmit}
            className="flex-1 rounded-lg bg-gray-900 py-2 text-sm font-semibold text-white transition disabled:cursor-not-allowed disabled:opacity-30"
          >
            {submitLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
