import { useState } from 'react'
import { useAuth } from '../../auth/useAuth'
import { useMeQuery } from '../../hooks/queries/useMe'
import { LoginModal } from '../auth/LoginModal'
import { FeedComposeModal } from './FeedComposeModal'
import { ProfileAvatar } from '../common/ProfileAvatar'

// 실제 토스증권 피드를 Playwright로 직접 열어 확인한 구조를 그대로 따른다:
// 아바타 + 플레이스홀더 문구 + "의견 남기기"가 하나의 경계선 박스 안에
// 있고, 비로그인 상태에서 누르면 로그인을 요구한다(실측 확인, QR 로그인
// 팝업). 로그인 상태에선 실제 글쓰기 모달(FeedComposeModal)을 띄운다.
export function FeedComposerCard() {
  const { isAuthenticated } = useAuth()
  const meQuery = useMeQuery(isAuthenticated)
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  const [composeModalOpen, setComposeModalOpen] = useState(false)

  function handleComposeClick() {
    if (!isAuthenticated) {
      setLoginModalOpen(true)
      return
    }
    setComposeModalOpen(true)
  }

  return (
    <section className="flex items-center gap-3 rounded-xl border border-gray-200 bg-white px-3 py-2 shadow-sm">
      <ProfileAvatar
        profileImageUrl={meQuery.data?.profileImageUrl}
        nickname={meQuery.data?.nickname}
        className="h-8 w-8"
      />
      {/* 예전엔 플레이스홀더 텍스트와 "의견 남기기" 버튼이 각각 별도
          버튼이었다 - 둘 다 같은 동작(글쓰기 모달 열기)인데 클릭 영역이
          나뉘어 있어 다른 기능처럼 오해할 수 있다는 피드백(2026-07-17) -
          하나의 버튼으로 합쳐 어디를 눌러도 동일하게 동작한다. */}
      <button
        type="button"
        onClick={handleComposeClick}
        className="flex flex-1 items-center justify-between gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-gray-50"
      >
        <span className="text-sm text-gray-400">오늘 시장 어떻게 보세요?</span>
        <span className="shrink-0 rounded-lg bg-gray-900 px-4 py-1.5 text-sm font-semibold text-white">
          의견 남기기
        </span>
      </button>
      <LoginModal open={loginModalOpen} onClose={() => setLoginModalOpen(false)} />

      <FeedComposeModal open={composeModalOpen} onClose={() => setComposeModalOpen(false)} />
    </section>
  )
}
