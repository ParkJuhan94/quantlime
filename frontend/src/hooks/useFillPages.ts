import { useEffect } from 'react'

interface PagedQuery {
  hasNextPage: boolean
  isFetchingNextPage: boolean
  isError: boolean
  fetchNextPage: () => unknown
  data?: { pages: unknown[] }
}

// 무한 쿼리의 첫 페이지만으로 봉이 너무 적게 그려지는 경우(60분봉은 1분봉 200개가 3~4개 봉, 월봉은 12개 안팎)에
// 화면에 보이기 전에 필요한 만큼 페이지를 자동으로 더 받는다. 호출 폭주를 막으려고 maxPages에서 멈추고, 그 뒤로는
// 사용자가 차트를 왼쪽으로 밀 때만 이어 받는다. 채우는 중이면 true를 돌려줘 화면이 로딩 표시를 유지하게 한다.
// enabled가 false면(해당 모드가 아닐 때) 아무것도 하지 않는다.
export function useFillPages(
  query: PagedQuery,
  barCount: number,
  { minBars, maxPages, enabled }: { minBars: number; maxPages: number; enabled: boolean },
): boolean {
  const { hasNextPage, isFetchingNextPage, isError, fetchNextPage } = query
  const pageCount = query.data?.pages.length ?? 0
  const needsMore = enabled && hasNextPage && !isError && pageCount > 0 && pageCount < maxPages && barCount < minBars

  useEffect(() => {
    if (needsMore && !isFetchingNextPage) {
      void fetchNextPage()
    }
  }, [needsMore, isFetchingNextPage, fetchNextPage])

  return needsMore
}
