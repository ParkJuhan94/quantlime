import type { NotificationStock } from '../types/notification'

export type ContentSegment = { kind: 'text'; text: string } | { kind: 'stock'; text: string; code: string }

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// 알림 본문에서 종목명을 찾아 종목 링크 조각으로 쪼갠다("삼성전자(A), 카카오" -> [삼성전자][(A), ][카카오]).
// 이름이 다른 이름의 일부일 수 있어(예: 삼성전자 / 삼성전자우) 긴 이름부터 맞추고, 같은 이름이 여러 번 나오면 모두 링크로 만든다.
// 본문에 이름이 없는 종목(사분면 알림의 "외 N건"에 가려진 종목)은 링크로 만들 곳이 없어 건너뛴다.
export function splitContentByStocks(content: string, stocks: NotificationStock[]): ContentSegment[] {
  const candidates = stocks.filter((stock) => stock.name.length > 0)
  if (candidates.length === 0) return [{ kind: 'text', text: content }]

  const codeByName = new Map(candidates.map((stock) => [stock.name, stock.code]))
  const names = [...codeByName.keys()].sort((a, b) => b.length - a.length)
  const pattern = new RegExp(`(${names.map(escapeRegExp).join('|')})`)

  // 캡처 그룹이 있는 split은 구분자(= 종목명)도 결과에 홀수 인덱스로 남긴다
  return content
    .split(pattern)
    .map((part, index): ContentSegment => {
      const code = index % 2 === 1 ? codeByName.get(part) : undefined
      return code ? { kind: 'stock', text: part, code } : { kind: 'text', text: part }
    })
    .filter((segment) => segment.text.length > 0)
}
