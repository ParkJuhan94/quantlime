// 백엔드 VideoRetentionService.RETENTION_DAYS와 반드시 같은 값을 유지해야
// 한다(BE/FE 공유 설정 파일이 없는 프로젝트라 각자 상수로 둠) - 이 값을
// 넘어가는 과거 데이터는 스케줄러가 실제로 삭제하므로, 프론트에서 그보다
// 과거 날짜를 선택할 수 있게 열어둬도 어차피 빈 목록만 보인다.
export const VIDEO_FEED_RETENTION_DAYS = 10

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

export function formatDayLabel(dateStr: string): string {
  const [, month, day] = parseDateParts(dateStr)
  const dateLabel = `${month}월 ${day}일`
  const ago = daysAgo(dateStr)
  return ago === 0 ? `오늘 · ${dateLabel}` : `${dateLabel} · ${ago}일 전`
}
