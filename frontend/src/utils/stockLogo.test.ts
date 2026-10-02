import { describe, expect, it } from 'vitest'
import { buildStockLogoUrl } from './stockLogo'

describe('buildStockLogoUrl', () => {
  it('종목 코드로 네이버 정적 로고 경로를 만든다(백엔드 StockMapper와 같은 규칙)', () => {
    expect(buildStockLogoUrl('005930')).toBe('https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/Stock005930.png')
  })
})
