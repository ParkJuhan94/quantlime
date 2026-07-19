// 색상 선택 바텀시트에 쓰는 32색 프리셋 - 참고 이미지와 동일하게 8개
// 색상(그레이/블루/퍼플/레드/오렌지/앰버/틸/그린) x 4단계 밝기(어두움→
// 밝음, 위에서 아래로)로 배열한다. grid-cols-8이라 배열 순서가 곧
// 행(밝기)×열(색상) 배치가 된다 - 순서를 바꾸면 그리드도 바뀌니 주의.
export const COLOR_PRESETS: string[] = [
  // 1행: 가장 어두운 톤
  '#1e293b', '#1e40af', '#6b21a8', '#991b1b', '#9a3412', '#b45309', '#115e59', '#166534',
  // 2행
  '#64748b', '#2563eb', '#9333ea', '#dc2626', '#ea580c', '#f59e0b', '#0d9488', '#16a34a',
  // 3행
  '#94a3b8', '#60a5fa', '#c084fc', '#f87171', '#fb923c', '#fcd34d', '#2dd4bf', '#4ade80',
  // 4행: 가장 밝은 톤
  '#e2e8f0', '#bfdbfe', '#e9d5ff', '#fecaca', '#fed7aa', '#fde68a', '#99f6e4', '#bbf7d0',
]

export const WIDTH_PRESETS = [1, 2, 3, 4] as const

export const LINE_STYLE_PRESETS: { value: 'solid' | 'dashed' | 'dotted'; label: string; dashArray?: string }[] = [
  { value: 'solid', label: '실선' },
  { value: 'dashed', label: '파선', dashArray: '6 4' },
  { value: 'dotted', label: '점선', dashArray: '1.5 3' },
]

export const PRICE_SOURCE_OPTIONS: { value: 'close' | 'open' | 'high' | 'low'; label: string }[] = [
  { value: 'close', label: '종가' },
  { value: 'open', label: '시가' },
  { value: 'high', label: '고가' },
  { value: 'low', label: '저가' },
]
