import { useEffect, useState } from 'react'
import { VIDEO_FEED_RETENTION_DAYS, clampToRetentionWindow, shiftDateString, todayDateString } from '../utils/dateFilter'

interface UseDateSkipNavigationOptions {
  storageKey: string
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

// sessionStorage는 비공개 브라우징 모드 등 일부 환경에서 접근 시 예외를
// 던질 수 있어 방어적으로 감싼다 - 실패해도 "오늘 기본값"으로 정상 동작해야
// 한다.
function readStoredDate(storageKey: string): string {
  try {
    const saved = sessionStorage.getItem(storageKey)
    return saved ? clampToRetentionWindow(saved) : todayDateString()
  } catch {
    return todayDateString()
  }
}

function writeStoredDate(storageKey: string, date: string): void {
  try {
    sessionStorage.setItem(storageKey, date)
  } catch {
    // 저장 실패해도 이번 세션 내 상태(React state)로는 계속 정상 동작한다.
  }
}

// VideoFeedPage(유튜브 요약)에서 추출한 날짜 네비게이션 로직(Phase 8 P7-F1) -
// 텔레그램 요약 피드도 동일한 UI/동작을 쓴다. 콘텐츠 유무에 따른 자동 건너뛰기
// 판단은 쿼리 결과를 아는 페이지 쪽 책임으로 남겨두고(순환 의존 방지), 이
// 훅은 날짜 상태·영속화·이전/다음 가능 여부·건너뛰기 실행만 담당한다.
// 보존 기간은 VIDEO_FEED_RETENTION_DAYS(14일)를 그대로 쓴다 - 텔레그램 요약
// 피드(TelegramPostRetentionService.RETENTION_DAYS)도 같은 값이라 재사용
// 가능. 값이 갈리게 되면 이 훅에 retentionDays 파라미터를 추가할 것.
//
// 30일 쿠키가 아니라 sessionStorage를 쓴다(2026-09-10) - "페이지 진입 시
// 기본은 오늘, 단 같은 세션 안에서 다른 날짜를 보고 있었다면 그걸 이어서
// 보여준다"는 요구사항 자체가 세션 스코프였다. 쿠키(30일 영속)로는 브라우저를
// 새로 열어도 몇 주 전에 보던 날짜가 그대로 복원돼 "오늘이 기본"이라는
// 기대와 어긋났다.
export function useDateSkipNavigation({
  storageKey,
}: UseDateSkipNavigationOptions): UseDateSkipNavigationResult {
  const [selectedDate, setSelectedDate] = useState(() => readStoredDate(storageKey))
  // 이전/다음 버튼 중 마지막으로 누른 방향(기본은 과거 방향) - 콘텐츠가 없는
  // 날짜를 만나면 이 방향으로 계속 넘겨 콘텐츠가 있는 날짜를 찾는다. 페이지
  // 최초 진입(오늘이 비어있는 경우)도 "최신 콘텐츠부터 보여준다"는 의미로
  // 과거 방향(-1)이 자연스럽다.
  const [skipDirection, setSkipDirection] = useState<1 | -1>(-1)

  useEffect(() => {
    writeStoredDate(storageKey, selectedDate)
  }, [storageKey, selectedDate])

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
