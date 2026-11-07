import { beforeEach, describe, expect, it, vi } from 'vitest'
import { rankingFilterStorage } from './rankingFilterStorage'

describe('rankingFilterStorage', () => {
  const store = new Map<string, string>()

  beforeEach(() => {
    store.clear()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
    })
  })

  it('저장값이 없으면 국내 탭으로 시작한다', () => {
    expect(rankingFilterStorage.read().scope).toBe('domestic')
  })

  it("없어진 '전체' 탭이 저장돼 있으면 국내로 읽고 다른 필터는 유지한다", () => {
    store.set('ql_ranking_filter', JSON.stringify({ scope: 'all', sortKey: 'amount' }))

    expect(rankingFilterStorage.read()).toMatchObject({ scope: 'domestic', sortKey: 'amount' })
  })

  it('해외 탭 저장값은 그대로 유지한다', () => {
    store.set('ql_ranking_filter', JSON.stringify({ scope: 'overseas' }))

    expect(rankingFilterStorage.read().scope).toBe('overseas')
  })
})
