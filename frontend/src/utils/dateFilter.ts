// 백엔드 VideoRetentionService.RETENTION_DAYS와 반드시 같은 값을 유지해야
// 한다(BE/FE 공유 설정 파일이 없는 프로젝트라 각자 상수로 둠) - 이 값을
// 넘어가는 과거 데이터는 스케줄러가 실제로 삭제하므로, 프론트에서 그보다
// 과거 날짜를 선택할 수 있게 열어둬도 어차피 빈 목록만 보인다.
export const VIDEO_FEED_RETENTION_DAYS = 14

// new Date("yyyy-MM-dd")로 파싱하면 UTC 자정으로 해석돼(스펙상 date-only
// 문자열은 UTC 기준) 이후 setDate 등 로컬 타임존 연산과 섞이면 자정
// 부근에서 하루씩 밀리는 문제가 생길 수 있다 - 연/월/일을 직접 분해해
// 로컬 타임존 생성자(new Date(y, m, d))로만 다루면 이 문제를 피할 수 있다.
function toDateString(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseDateParts(dateStr: string): [number, number, number] {
  const [year, month, day] = dateStr.split('-').map(Number)
  return [year, month, day]
}

export function todayDateString(): string {
  return toDateString(new Date())
}

export function shiftDateString(dateStr: string, deltaDays: number): string {
  const [year, month, day] = parseDateParts(dateStr)
  return toDateString(new Date(year, month - 1, day + deltaDays))
}

// yyyy-MM-dd는 제로패딩된 ISO 형식이라 문자열 비교 자체가 곧 날짜 비교와
// 동치다 - Date 객체로 다시 변환할 필요가 없다.
export function clampToRetentionWindow(dateStr: string): string {
  const oldest = shiftDateString(todayDateString(), -VIDEO_FEED_RETENTION_DAYS)
  const newest = todayDateString()
  if (dateStr < oldest) return oldest
  if (dateStr > newest) return newest
  return dateStr
}

export function daysAgo(dateStr: string): number {
  const [ty, tm, td] = parseDateParts(todayDateString())
  const [dy, dm, dd] = parseDateParts(dateStr)
  const diffMs = new Date(ty, tm - 1, td).getTime() - new Date(dy, dm - 1, dd).getTime()
  return Math.round(diffMs / 86_400_000)
}

const WEEKDAY_LABELS = ['일', '월', '화', '수', '목', '금', '토']

export function formatDayLabel(dateStr: string): string {
  const [year, month, day] = parseDateParts(dateStr)
  const weekday = WEEKDAY_LABELS[new Date(year, month - 1, day).getDay()]
  const dateLabel = `${month}월 ${day}일(${weekday})`
  const ago = daysAgo(dateStr)
  return ago === 0 ? `오늘 · ${dateLabel}` : `${dateLabel} · ${ago}일 전`
}

// 영상 카드의 "N일 전" 표기가 위 필터 라벨(daysAgo, 자정 기준 날짜 차이)과
// 하루씩 어긋나던 버그 수정(2026-08-02) - 커뮤니티 피드에서 쓰는
// formatRelativeTime(utils/relativeTime.ts)은 "정확히 24시간이 지났는가"를
// 기준으로 하는 롤링 윈도우라, 예를 들어 어제 23시에 올라온 영상을 오늘
// 09시에 보면 아직 24시간이 안 지나 그쪽은 "10시간"으로 표시하는데 필터는
// 자정 기준으로 이미 "1일 전"이라 서로 다른 값이 보였다. 커뮤니티 피드
// (FeedPostCard 등)는 진짜 실시간 경과 표기가 맞는 UX라 그 함수 자체는
// 건드리지 않고, 영상 피드 전용으로 필터와 동일한 자정 기준 계산을 쓴다.
export function formatVideoPublishedAt(isoDateTime: string): string {
  const diffMs = Date.now() - new Date(isoDateTime).getTime()
  const diffMinutes = Math.floor(diffMs / 60_000)
  if (diffMinutes < 1) return '방금'
  if (diffMinutes < 60) return `${diffMinutes}분`
  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours}시간`
  return `${daysAgo(toDateString(new Date(isoDateTime)))}일`
}
