import { useEffect, useState } from 'react'
import { getCookie, setCookie } from '../utils/cookie'
import { VIDEO_FEED_RETENTION_DAYS, clampToRetentionWindow, shiftDateString, todayDateString } from '../utils/dateFilter'

interface UseDateSkipNavigationOptions {
  cookieName: string
  cookieDays?: number
}

interface UseDateSkipNavigationResult {
  selectedDate: string
  canGoPrev: boolean
  canGoNext: boolean
  // 마지막으로 누른 방향으로 더 넘어갈 여지가 있는지 - 콘텐츠가 없는 날짜를
  // 자동으로 건너뛰는 skipDate() 호출 여부를 호출부(쿼리 결과를 아는 쪽)가
  // 판단할 때 쓴다.
  canSkipFurther: boolean
  goPrev: () => void
  goNext: () => void
  // 콘텐츠가 없는 날짜를 만났을 때 마지막 방향으로 하루 더 건너뛴다 -
  // 쿼리 결과(isLoading/hasContent)는 이 훅이 알 수 없으므로, 호출부가
  // "지금 날짜에 콘텐츠가 없다"고 판단한 시점에 직접 불러야 한다.
  skipDate: () => void
}

function initialDate(cookieName: string): string {
  const saved = getCookie(cookieName)
  return saved ? clampToRetentionWindow(saved) : todayDateString()
}

// VideoFeedPage(유튜브 요약)에서 추출한 날짜 네비게이션 로직(Phase 8 P7-F1) -
// 텔레그램 요약 피드도 동일한 UI/동작을 쓴다. 콘텐츠 유무에 따른 자동 건너뛰기
// 판단은 쿼리 결과를 아는 페이지 쪽 책임으로 남겨두고(순환 의존 방지), 이
// 훅은 날짜 상태·쿠키 영속화·이전/다음 가능 여부·건너뛰기 실행만 담당한다.
// 보존 기간은 VIDEO_FEED_RETENTION_DAYS(14일)를 그대로 쓴다 - 텔레그램 요약
// 피드(TelegramPostRetentionService.RETENTION_DAYS)도 같은 값이라 재사용
// 가능. 값이 갈리게 되면 이 훅에 retentionDays 파라미터를 추가할 것.
export function useDateSkipNavigation({
  cookieName,
  cookieDays = 30,
}: UseDateSkipNavigationOptions): UseDateSkipNavigationResult {
  const [selectedDate, setSelectedDate] = useState(() => initialDate(cookieName))
  // 이전/다음 버튼 중 마지막으로 누른 방향(기본은 과거 방향) - 콘텐츠가 없는
  // 날짜를 만나면 이 방향으로 계속 넘겨 콘텐츠가 있는 날짜를 찾는다. 페이지
  // 최초 진입(오늘이 비어있는 경우)도 "최신 콘텐츠부터 보여준다"는 의미로
  // 과거 방향(-1)이 자연스럽다.
  const [skipDirection, setSkipDirection] = useState<1 | -1>(-1)

  useEffect(() => {
    setCookie(cookieName, selectedDate, cookieDays)
  }, [cookieName, cookieDays, selectedDate])

  const oldestSelectableDate = shiftDateString(todayDateString(), -VIDEO_FEED_RETENTION_DAYS)
  const canGoPrev = selectedDate > oldestSelectableDate
  const canGoNext = selectedDate < todayDateString()
  const canSkipFurther =
    skipDirection === -1 ? selectedDate > oldestSelectableDate : selectedDate < todayDateString()

  function goPrev() {
    setSkipDirection(-1)
    setSelectedDate((prev) => shiftDateString(prev, -1))
  }
  function goNext() {
    setSkipDirection(1)
    setSelectedDate((prev) => shiftDateString(prev, 1))
  }
  function skipDate() {
    setSelectedDate((prev) => shiftDateString(prev, skipDirection))
  }

  return { selectedDate, canGoPrev, canGoNext, canSkipFurther, goPrev, goNext, skipDate }
}
