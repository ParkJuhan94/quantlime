import { describe, expect, it } from 'vitest'
import {
  canRestart,
  extractExecutionId,
  formatDateTime,
  formatDuration,
  hasRunning,
  isRunning,
  jobLabel,
  restartableExecutionIds,
  statusLabel,
} from './batchExecution'

describe('상태 판단', () => {
  it('시작 전·실행 중·중단하는 중은 진행 중으로 본다', () => {
    for (const status of ['STARTING', 'STARTED', 'STOPPING']) expect(isRunning(status)).toBe(true)
    for (const status of ['COMPLETED', 'FAILED', 'STOPPED']) expect(isRunning(status)).toBe(false)
  })

  it('FAILED·STOPPED만 이어서 실행할 수 있다', () => {
    expect(canRestart('FAILED')).toBe(true)
    expect(canRestart('STOPPED')).toBe(true)
    expect(canRestart('COMPLETED')).toBe(false)
    expect(canRestart('STARTED')).toBe(false)
  })

  it('목록에 진행 중인 실행이 하나라도 있으면 자동 갱신 대상이다', () => {
    expect(hasRunning([{ status: 'COMPLETED' }, { status: 'STARTED' }])).toBe(true)
    expect(hasRunning([{ status: 'COMPLETED' }, { status: 'FAILED' }])).toBe(false)
    expect(hasRunning([])).toBe(false)
  })
})

describe('라벨', () => {
  it('상태와 Job 이름을 한글로 보여주고, 모르는 값은 원문을 그대로 쓴다', () => {
    expect(statusLabel('FAILED')).toBe('실패')
    expect(statusLabel('STARTING')).toBe('실행 중')
    expect(statusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(jobLabel('scoreRebuildJob')).toBe('스코어 재구성')
    expect(jobLabel('backtestWeeklyJob')).toBe('주간 백테스트')
    expect(jobLabel('newJob')).toBe('newJob')
  })
})

describe('extractExecutionId', () => {
  it('시작 응답 문자열에서 실행 ID를 꺼낸다', () => {
    expect(extractExecutionId('스코어 재구성 시작: from=2026-10-20, executionId=4')).toBe(4)
    expect(extractExecutionId('유니버스 백테스트 시작: executionId=128')).toBe(128)
  })

  it('실행 ID가 없으면 null이다', () => {
    expect(extractExecutionId('이미 실행 중인 배치가 있습니다')).toBeNull()
    expect(extractExecutionId('')).toBeNull()
  })
})

describe('formatDateTime', () => {
  it('시간대 변환 없이 월-일 시:분:초로 줄인다', () => {
    expect(formatDateTime('2026-10-23T01:07:50.669')).toBe('10-23 01:07:50')
    expect(formatDateTime('2026-12-31T23:59:59')).toBe('12-31 23:59:59')
  })

  it('값이 없으면 "-", 모르는 형식은 원문을 그대로 보여준다', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime('어제')).toBe('어제')
  })
})

describe('formatDuration', () => {
  it('초, 분·초, 시간·분 단위로 줄여서 보여준다', () => {
    expect(formatDuration('2026-10-23T01:00:00', '2026-10-23T01:00:42')).toBe('42초')
    expect(formatDuration('2026-10-23T01:00:00', '2026-10-23T01:12:05')).toBe('12분 5초')
    expect(formatDuration('2026-10-23T01:00:00', '2026-10-23T02:32:10')).toBe('1시간 32분')
  })

  it('날짜가 바뀌어도 차이를 맞게 계산한다', () => {
    expect(formatDuration('2026-10-22T23:50:00', '2026-10-23T00:10:00')).toBe('20분 0초')
  })

  it('종료 전이거나 시각이 없으면 "-"다', () => {
    expect(formatDuration('2026-10-23T01:00:00', null)).toBe('-')
    expect(formatDuration(null, '2026-10-23T01:00:00')).toBe('-')
  })
})

describe('restartableExecutionIds', () => {
  it('실패·중단이면서 그 JobInstance의 가장 최근 실행일 때만 이어서 실행할 수 있다', () => {
    const executions = [
      { executionId: 5, jobInstanceId: 1, status: 'COMPLETED' }, // #3을 이어서 실행해 끝난 실행
      { executionId: 4, jobInstanceId: 2, status: 'FAILED' }, // 아직 이어서 실행하지 않은 실패
      { executionId: 3, jobInstanceId: 1, status: 'FAILED' }, // 이미 #5로 이어서 실행됨
      { executionId: 2, jobInstanceId: 3, status: 'STOPPED' },
      { executionId: 1, jobInstanceId: 4, status: 'COMPLETED' },
    ]

    expect(restartableExecutionIds(executions)).toEqual(new Set([4, 2]))
  })

  it('이어서 실행한 실행이 아직 진행 중이어도 이전 실패는 다시 이어서 실행할 수 없다', () => {
    const executions = [
      { executionId: 8, jobInstanceId: 7, status: 'STARTED' },
      { executionId: 6, jobInstanceId: 7, status: 'FAILED' },
    ]

    expect(restartableExecutionIds(executions).size).toBe(0)
  })

  it('목록이 비어 있으면 빈 집합이다', () => {
    expect(restartableExecutionIds([]).size).toBe(0)
  })
})
