// SharedArray는 VU 수와 무관하게 메모리에 1부(사본 없음)만 올린다.
// 2,596개 국내 상장 종목 코드를 VU마다 복사하면 수천 VU에서 수백 MB가 샌다.
import { SharedArray } from 'k6/data';

export const stockCodes = new SharedArray('stock-codes', () =>
  JSON.parse(open('../data/stock-codes.json'))
);

export const searchKeywords = new SharedArray('search-keywords', () =>
  JSON.parse(open('../data/search-keywords.json'))
);

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// 캐시 히트/미스 비율을 의도적으로 통제하고 싶을 때 쓴다: 상위 N개(정렬
// 순서 고정)만 뽑으면 Redis/캐시 히트율이 올라가고, 전체에서 뽑으면
// 콜드 경로(예: 가격 3-쿼리 폴백)를 더 많이 때린다.
export function pickHot(arr, n = 50) {
  const bound = Math.min(n, arr.length);
  return arr[Math.floor(Math.random() * bound)];
}
