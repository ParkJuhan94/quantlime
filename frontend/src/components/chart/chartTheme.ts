import type { Theme } from '../../storage/themeStorage'

// lightweight-charts는 색을 CSS가 아니라 JS 옵션으로 받아서 index.css의 다크
// 팔레트가 닿지 않는다. 라이트 값은 기존 하드코딩과 같고, 다크 값은 index.css의
// 카드(white)/gray 스케일에 맞춘 것이다.
const PALETTE = {
  light: { text: '#374151', background: '#ffffff', grid: '#f3f4f6', border: '#e5e7eb' },
  dark: { text: '#c3cbd9', background: '#121a2d', grid: '#1c2740', border: '#263350' },
} as const

export function chartPalette(theme: Theme) {
  return PALETTE[theme]
}
