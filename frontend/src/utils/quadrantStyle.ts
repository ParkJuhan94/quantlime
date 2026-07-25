// 사분면(추세추종 x 평균회귀) 4가지 라벨의 색상 매핑. 국내 주식 관례(상승=빨강,
// 하락=파랑, CLAUDE.md §7)를 따르되, 같은 방향 안에서도 좋은/주의 신호를
// 채도로 구분한다 - 추세와 단기 지표가 같은 방향(둘 다 긍정 또는 둘 다
// 부정)일 때 더 진한 색, 서로 엇갈릴 때 옅은 색을 쓴다.
export const QUADRANT_LINE_COLORS: Record<string, string> = {
  '상승추세 눌림목': '#dc2626', // red-600 - 추세도 강세, 단기 되돌림도 매수 신호(가장 긍정적)
  '추세 연장·과열': '#fb923c', // orange-400 - 추세는 강세지만 단기 과열
  '낙폭과대·위험': '#93c5fd', // blue-300 - 단기 반등 조짐은 있으나 추세 자체는 약세
  '하락추세 반등실패': '#2563eb', // blue-600 - 추세도 단기도 약세(가장 부정적)
}

export const QUADRANT_BADGE_STYLES: Record<string, string> = {
  '상승추세 눌림목': 'bg-red-100 text-red-700',
  '추세 연장·과열': 'bg-orange-100 text-orange-700',
  '낙폭과대·위험': 'bg-blue-100 text-blue-700',
  '하락추세 반등실패': 'bg-blue-600 text-white',
}

export const QUADRANT_ORDER = ['상승추세 눌림목', '추세 연장·과열', '낙폭과대·위험', '하락추세 반등실패']
