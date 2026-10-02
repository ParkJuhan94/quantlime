import { describe, expect, it } from 'vitest'
import { QUADRANT_BADGE_STYLES, QUADRANT_LINE_COLORS, QUADRANT_ORDER } from './quadrantStyle'

// 백엔드가 내려주는 사분면 한글 라벨과 이 매핑 키가 어긋나면 화면에서 색이 조용히 빠진다 -
// 세 상수가 같은 4개 라벨을 빠짐없이 공유하는지 고정한다
describe('quadrantStyle', () => {
  it('표시 순서는 4개이고 중복이 없다', () => {
    expect(QUADRANT_ORDER).toHaveLength(4)
    expect(new Set(QUADRANT_ORDER).size).toBe(4)
  })

  it('모든 라벨이 선 색상과 배지 스타일 양쪽에 정의돼 있다', () => {
    for (const label of QUADRANT_ORDER) {
      expect(QUADRANT_LINE_COLORS[label], `${label} 선 색상`).toMatch(/^#[0-9a-f]{6}$/)
      expect(QUADRANT_BADGE_STYLES[label], `${label} 배지 스타일`).toBeTruthy()
    }
  })

  it('두 매핑에는 표시 순서에 없는 라벨이 섞여 있지 않다', () => {
    expect(Object.keys(QUADRANT_LINE_COLORS).sort()).toEqual([...QUADRANT_ORDER].sort())
    expect(Object.keys(QUADRANT_BADGE_STYLES).sort()).toEqual([...QUADRANT_ORDER].sort())
  })
})
