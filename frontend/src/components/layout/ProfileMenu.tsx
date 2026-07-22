import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMeQuery } from '../../hooks/queries/useMe'
import { ProfileAvatar } from '../common/ProfileAvatar'

interface ProfileMenuProps {
  onLogout: () => void
}

export function ProfileMenu({ onLogout }: ProfileMenuProps) {
  const meQuery = useMeQuery(true)
  const me = meQuery.data
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  // 메뉴 바깥을 클릭하면 닫는다.
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

  function handleMyInfo() {
    setOpen(false)
    navigate('/me')
  }

  function handleSubscription() {
    setOpen(false)
    navigate('/subscribe')
  }

  function handleLogout() {
    setOpen(false)
    onLogout()
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label="프로필 메뉴 열기"
        className="rounded-full"
      >
        <ProfileAvatar profileImageUrl={me?.profileImageUrl} nickname={me?.nickname} className="h-9 w-9" />
      </button>

      {open && (
        <div className="absolute right-0 top-11 z-50 w-64 rounded-2xl border border-gray-100 bg-white p-4 shadow-lg">
          <div className="flex items-center gap-3 px-1 pb-3">
            <ProfileAvatar profileImageUrl={me?.profileImageUrl} nickname={me?.nickname} className="h-10 w-10" />
            <span className="truncate text-sm font-semibold text-gray-900">{me?.nickname}</span>
          </div>

          <div className="border-t border-gray-100 pt-2">
            <button
              type="button"
              onClick={handleMyInfo}
              className="flex w-full items-center justify-between rounded-lg px-2 py-2 text-left text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              내 정보
              <span className="text-gray-300">›</span>
            </button>
            <button
              type="button"
              onClick={handleSubscription}
              className="flex w-full items-center justify-between rounded-lg px-2 py-2 text-left text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              구독 관리
              <span className="text-gray-300">›</span>
            </button>
            <button
              type="button"
              onClick={handleLogout}
              className="flex w-full items-center rounded-lg px-2 py-2 text-left text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              로그아웃
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
