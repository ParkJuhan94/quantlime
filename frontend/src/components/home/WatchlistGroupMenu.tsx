import { useEffect, useRef, useState } from 'react'

interface WatchlistGroupMenuProps {
  onEditGroups: () => void
  onCreateGroup: () => void
}

// "관심" 패널 헤더의 "···" 버튼 - 관심 그룹 편집(관리 모달 열기)과
// 새 관심 그룹 만들기(생성 다이얼로그 열기) 두 액션을 제공한다.
export function WatchlistGroupMenu({ onEditGroups, onCreateGroup }: WatchlistGroupMenuProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleClickOutside(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    window.addEventListener('mousedown', handleClickOutside)
    return () => window.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label="관심 그룹 메뉴"
        className="flex h-6 w-6 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100"
      >
        ···
      </button>

      {open && (
        <div className="absolute right-0 top-7 z-20 w-40 rounded-xl border border-gray-100 bg-white p-1.5 shadow-lg">
          <button
            type="button"
            onClick={() => {
              setOpen(false)
              onEditGroups()
            }}
            className="w-full rounded-lg px-2.5 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
          >
            관심 그룹 편집
          </button>
          <button
            type="button"
            onClick={() => {
              setOpen(false)
              onCreateGroup()
            }}
            className="w-full rounded-lg px-2.5 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
          >
            새 관심 그룹 만들기
          </button>
        </div>
      )}
    </div>
  )
}
