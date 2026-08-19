// 혼합 사용자 여정 - 실제 화면 흐름(홈/검색→상세/피드)을 섞어 동시에
// 램프한다. 엔드포인트별 한계(endpoint-ramp.js)는 개별 수치일 뿐 합쳐지지
// 않는다 - 전부 같은 HikariCP 풀(10)과 2 vCPU를 공유하므로, 시스템
// 전체의 무릎(knee)은 이 시나리오로만 찾을 수 있다.
//
// canary 시나리오(고정 1rps, /api/health만)를 상시 병행한다 - 다른
// 시나리오가 앱을 포화시켰을 때 가장 싼 요청조차 느려지는지가 "SQL이
// 느린 것"과 "스레드풀/accept 큐가 포화된 것"을 구분하는 핵심 신호다.
import { group, sleep } from 'k6';
import { safeGet } from '../lib/guard.js';
import { stockCodes, searchKeywords, pick, pickHot } from '../lib/data.js';
import { abortThresholds } from '../config/options.js';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    // 홈 진입 - 가장 빈번. 지수+랭킹 위젯이 한 화면에 같이 뜬다.
    home: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 150,
      maxVUs: 800,
      exec: 'home',
      tags: { journey: 'home' },
      stages: [
        { target: 20, duration: '1m' },
        { target: 20, duration: '2m' },
        { target: 60, duration: '1m' },
        { target: 60, duration: '2m' },
        { target: 150, duration: '1m' },
        { target: 150, duration: '2m' },
        { target: 300, duration: '1m' },
        { target: 300, duration: '2m' },
        { target: 0, duration: '30s' },
      ],
    },
    // 검색 → 종목 상세. 홈의 약 40% 빈도로 가정(실측 후 조정).
    browse: {
      executor: 'ramping-arrival-rate',
      startRate: 2,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 600,
      exec: 'browse',
      tags: { journey: 'browse' },
      stages: [
        { target: 8, duration: '1m' },
        { target: 8, duration: '2m' },
        { target: 24, duration: '1m' },
        { target: 24, duration: '2m' },
        { target: 60, duration: '1m' },
        { target: 60, duration: '2m' },
        { target: 120, duration: '1m' },
        { target: 120, duration: '2m' },
        { target: 0, duration: '30s' },
      ],
    },
    // 피드 열람. 저빈도지만 telegram N+1이 섞여 있어 풀을 갉아먹는다.
    feeds: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 60,
      maxVUs: 300,
      exec: 'feeds',
      tags: { journey: 'feeds' },
      stages: [
        { target: 4, duration: '1m' },
        { target: 4, duration: '2m' },
        { target: 12, duration: '1m' },
        { target: 12, duration: '2m' },
        { target: 30, duration: '1m' },
        { target: 30, duration: '2m' },
        { target: 60, duration: '1m' },
        { target: 60, duration: '2m' },
        { target: 0, duration: '30s' },
      ],
    },
    // 상시 저부하 프로브 - 위 세 시나리오가 앱을 포화시켰을 때, 가장 싼
    // 요청조차 느려지는지 재는 카나리아. 절대 램프하지 않는다.
    canary: {
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '1s',
      duration: '13m',
      preAllocatedVUs: 5,
      maxVUs: 20,
      exec: 'canary',
      tags: { journey: 'canary' },
    },
  },
  thresholds: {
    ...abortThresholds({ p95Ms: 5000, failRate: 0.15 }),
    // 카나리아만 따로 본다 - 이 값이 튀는 순간이 시스템 포화 시점이다.
    'http_req_duration{journey:canary}': ['p(95)<500'],
  },
};

export function home() {
  group('home', () => {
    safeGet('/api/market/indices', { name: 'indices' });
    safeGet('/api/market/ranking?scope=domestic&sort=gainers&limit=20', { name: 'ranking' });
  });
  sleep(Math.random() * 2 + 1); // think time 1~3초
}

export function browse() {
  group('browse', () => {
    safeGet(
      `/api/stocks/search?q=${encodeURIComponent(pick(searchKeywords))}&page=0&size=20`,
      { name: 'search' }
    );
    sleep(1);
    const code = pickHot(stockCodes, 200);
    safeGet(`/api/stocks/${code}`, { name: 'stock-detail' });
    safeGet(`/api/stocks/${code}/price`, { name: 'price' });
    safeGet(`/api/stocks/${code}/chart?days=90`, { name: 'chart' });
  });
  sleep(Math.random() * 3 + 2);
}

export function feeds() {
  group('feeds', () => {
    safeGet('/api/video-feed/videos?page=0&size=20', { name: 'video-feed' });
    safeGet('/api/feed/posts?page=0&size=20', { name: 'feed' });
    safeGet('/api/telegram-feed/digests?page=0&size=20', { name: 'telegram-feed' });
  });
  sleep(Math.random() * 4 + 3);
}

export function canary() {
  safeGet('/api/health', { name: 'canary-health' });
}
