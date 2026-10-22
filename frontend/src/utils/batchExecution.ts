// 배치 실행 이력 화면의 순수 함수들. 서버가 주는 시각은 시간대 없는 KST 문자열이라 Date로 변환하지 않고 문자열을 분해해서 쓴다
// (브라우저 시간대가 달라도 같은 값이 보이게 하고, UTC 변환으로 날짜가 하루 어긋나는 함정을 피한다).

const RUNNING_STATUSES = ['STARTING', 'STARTED', 'STOPPING']
const RESTARTABLE_STATUSES = ['FAILED', 'STOPPED']

const STATUS_LABELS: Record<string, string> = {
  STARTING: '실행 중',
  STARTED: '실행 중',
  STOPPING: '중단하는 중',
  STOPPED: '중단',
  FAILED: '실패',
  COMPLETED: '완료',
  ABANDONED: '포기',
}

const JOB_LABELS: Record<string, string> = {
  scoreRebuildJob: '스코어 재구성',
  backtestWeeklyJob: '주간 백테스트',
  backtestDatasetJob: '백테스트 데이터 준비',
  backtestUniverseJob: '유니버스 백테스트',
  crossSectionalBacktestJob: '횡단면 백테스트',
}

export function isRunning(status: string): boolean {
  return RUNNING_STATUSES.includes(status)
}

export function canRestart(status: string): boolean {
  return RESTARTABLE_STATUSES.includes(status)
}

export function hasRunning(executions: { status: string }[]): boolean {
  return executions.some((execution) => isRunning(execution.status))
}

export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status
}

// 모르는 Job은 이름 그대로 보여준다(새 Job이 추가돼도 화면이 깨지지 않게)
export function jobLabel(jobName: string): string {
  return JOB_LABELS[jobName] ?? jobName
}

// "스코어 재구성 시작: from=2026-10-20, executionId=4" 같은 시작 응답에서 실행 ID를 꺼낸다
export function extractExecutionId(text: string): number | null {
  const match = /executionId=(\d+)/.exec(text)
  return match ? Number(match[1]) : null
}

const DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/

// "2026-10-23T01:07:50.669" -> "10-23 01:07:50", 값이 없으면 "-"
export function formatDateTime(value: string | null): string {
  if (!value) return '-'
  const match = DATE_TIME_PATTERN.exec(value)
  if (!match) return value
  const [, , month, day, hour, minute, second] = match
  return `${month}-${day} ${hour}:${minute}:${second}`
}

function toLocalDate(value: string): Date | null {
  const match = DATE_TIME_PATTERN.exec(value)
  if (!match) return null
  const [, year, month, day, hour, minute, second] = match.map(Number)
  return new Date(year, month - 1, day, hour, minute, second)
}

// 시작·종료가 같은 시간대의 문자열이라 차이는 브라우저 시간대와 무관하다. 종료 전(실행 중)에는 현재 시각을 알 수 없어 "-"를 돌려준다
export function formatDuration(start: string | null, end: string | null): string {
  if (!start || !end) return '-'
  const startDate = toLocalDate(start)
  const endDate = toLocalDate(end)
  if (!startDate || !endDate) return '-'
  const totalSeconds = Math.max(0, Math.round((endDate.getTime() - startDate.getTime()) / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) return `${hours}시간 ${minutes}분`
  if (minutes > 0) return `${minutes}분 ${seconds}초`
  return `${seconds}초`
}
