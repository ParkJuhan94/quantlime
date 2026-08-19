// 스모크 테스트 - 하네스/시딩/토큰/구독 게이트가 전부 맞물려 있는지
// 20~30초 안에 확인한다. 다른 시나리오를 돌리기 전에 항상 이것부터.
import { check } from 'k6';
import { safeGet } from '../lib/guard.js';
import { premiumUserForVu, bearer } from '../lib/auth.js';
import { stockCodes, searchKeywords } from '../lib/data.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1.00'],
    http_req_failed: ['rate==0.00'],
  },
};

export default function () {
  const code = stockCodes[0];

  check(safeGet('/api/health', { name: 'health' }), {
    'health 200': (r) => r.status === 200,
  });
  check(
    safeGet(`/api/stocks/search?q=${encodeURIComponent(searchKeywords[0])}&page=0&size=20`, {
      name: 'search',
    }),
    { 'search 200': (r) => r.status === 200 }
  );
  check(safeGet(`/api/stocks/${code}/price`, { name: 'price' }), {
    'price 200': (r) => r.status === 200,
  });
  check(safeGet(`/api/stocks/${code}/chart?days=90`, { name: 'chart' }), {
    'chart 200': (r) => r.status === 200,
  });
  check(safeGet('/api/market/indices', { name: 'indices' }), {
    'indices 200': (r) => r.status === 200,
  });
  check(
    safeGet('/api/market/ranking?scope=domestic&sort=gainers&limit=20', { name: 'ranking' }),
    { 'ranking 200': (r) => r.status === 200 }
  );
  check(safeGet('/api/video-feed/videos?page=0&size=20', { name: 'video-feed' }), {
    'video-feed 200': (r) => r.status === 200,
  });
  check(safeGet('/api/feed/posts?page=0&size=20', { name: 'feed' }), {
    'feed 200': (r) => r.status === 200,
  });
  check(safeGet('/api/telegram-feed/digests?page=0&size=20', { name: 'telegram-feed' }), {
    'telegram-feed 200': (r) => r.status === 200,
  });

  // 시딩+토큰+구독 게이트가 실제로 맞물렸는지의 최종 검증. watchlistOnly=true는
  // 관심종목만 조회하므로 B1(전체 랭킹, 116초 상관 서브쿼리)을 타지 않는다 -
  // 그 경로는 premium-scores.js가 opt-in으로 별도 측정한다.
  const premiumUser = premiumUserForVu();
  const scoresRes = safeGet(
    '/api/dashboard/scores?watchlistOnly=true&limit=10',
    { name: 'scores-watchlist' },
    bearer(premiumUser)
  );
  check(scoresRes, {
    'scores-watchlist 200 (구독 시딩 확인 - 403/500이면 시딩을 재확인할 것)': (r) =>
      r.status === 200,
  });
}
