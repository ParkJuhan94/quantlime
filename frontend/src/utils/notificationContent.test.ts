import { describe, expect, it } from 'vitest'
import { splitContentByStocks } from './notificationContent'

const SAMSUNG = { code: '005930', name: '삼성전자' }
const HYNIX = { code: '000660', name: 'SK하이닉스' }

describe('splitContentByStocks', () => {
  it('종목이 없으면 본문 전체가 한 덩어리 텍스트다', () => {
    expect(splitContentByStocks('점검 안내', [])).toEqual([{ kind: 'text', text: '점검 안내' }])
  })

  it('본문 속 종목명만 링크 조각으로 나누고 나머지 글자는 그대로 둔다', () => {
    expect(splitContentByStocks('삼성전자(STRONG_BUY), SK하이닉스(BUY)', [SAMSUNG, HYNIX])).toEqual([
      { kind: 'stock', text: '삼성전자', code: '005930' },
      { kind: 'text', text: '(STRONG_BUY), ' },
      { kind: 'stock', text: 'SK하이닉스', code: '000660' },
      { kind: 'text', text: '(BUY)' },
    ])
  })

  it('다른 이름의 일부인 이름은 긴 이름이 먼저 맞는다', () => {
    const segments = splitContentByStocks('삼성전자우 상승', [SAMSUNG, { code: '005935', name: '삼성전자우' }])
    expect(segments[0]).toEqual({ kind: 'stock', text: '삼성전자우', code: '005935' })
  })

  it('같은 이름이 여러 번 나오면 모두 링크가 되고, 정규식 특수문자가 있는 이름도 안전하다', () => {
    const segments = splitContentByStocks('A+B와 A+B', [{ code: '111111', name: 'A+B' }])
    expect(segments.filter((segment) => segment.kind === 'stock')).toHaveLength(2)
  })

  it('본문에 이름이 없는 종목은 건너뛴다(링크 조각이 생기지 않는다)', () => {
    expect(splitContentByStocks('삼성전자 외 1건', [SAMSUNG, HYNIX]).map((s) => s.kind)).toEqual(['stock', 'text'])
  })
})
