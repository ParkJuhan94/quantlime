import type { RefObject } from 'react'
import type { IChartApi, LogicalRange } from 'lightweight-charts'

// 시야의 왼쪽 끝이 데이터 첫 봉에서 이 개수 이내로 가까워지면 "과거 구간 더 불러오기"를 요청한다.
const REACH_START_BARS = 15

// 사용자가 차트를 왼쪽으로 밀어 가장 오래된 봉 근처에 닿으면 콜백을 부른다 - 버튼을 눌러 이전 구간을 불러오던
// 방식 대신 스크롤만으로 이어 받게 한다. 콜백은 ref로 받아서 부모가 새 함수를 넘겨도 차트를 다시 만들지 않는다.
// 호출이 연달아 와도 부모(useInfiniteQuery)가 hasNextPage/isFetchingNextPage로 중복을 막는다.
export function subscribeReachStart(chart: IChartApi, callbackRef: RefObject<(() => void) | undefined>): void {
  chart.timeScale().subscribeVisibleLogicalRangeChange((range: LogicalRange | null) => {
    if (range && range.from < REACH_START_BARS) {
      callbackRef.current?.()
    }
  })
}
