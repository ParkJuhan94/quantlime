// 엔드포인트별 한계치 탐색. TARGET=<name>으로 대상을 고른다.
//
//   ./load-test/run/run.sh endpoint-ramp TARGET=search
//
// ramping-vus(closed-loop)가 아니라 ramping-arrival-rate(open-loop)를 쓴다 -
// closed-loop는 앱이 느려지면 offered load가 같이 줄어 breaking point를
// 못 본다. open-loop는 목표 처리율을 계속 밀어붙여서, 앱이 못 따라가면
// dropped_iterations가 쌓이고 지연이 수직 상승한다. 이 신호가 곧 한계점이다.
//
// 계단(20초 램프 + 2분 평탄부)마다 (목표 rps, 실제 rps, p95, p99,
// dropped_iterations)를 기록한다 - 실제 rps가 목표를 못 따라가기 시작하는
// 첫 계단이 그 엔드포인트의 용량이다.
import { safeGet } from '../lib/guard.js';
import { stockCodes, searchKeywords, pick, pickHot } from '../lib/data.js';
import { durByEndpoint, serverErrors } from '../lib/metrics.js';
import { abortThresholds } from '../config/options.js';

// 엔드포인트별 프로파일. 시작값/상한은 이번 세션에 실측한 비용에서
// 역산했다(각 항목 주석 참고 - docs/LOAD_TESTING.md와 plan 문서에 근거 기록).
const PROFILES = {
  // 정적 200, I/O 없음. nginx+톰캣+2 vCPU 자체 상한을 잰다.
  health: {
    startRate: 100,
    steps: [500, 1500, 3000, 5000],
    maxVUs: 500,
    req: () => safeGet('/api/health', { name: 'health' }),
  },
  // 선행 와일드카드 LIKE 3개 OR + 8,958행 풀스캔, 인덱스/캐시 없음(B3).
  // HikariCP 10커넥션이 곧 동시성 상한이라 낮게 시작한다.
  search: {
    startRate: 5,
    steps: [25, 75, 150, 300],
    maxVUs: 400,
    req: () =>
      safeGet(`/api/stocks/search?q=${encodeURIComponent(pick(searchKeywords))}&page=0&size=20`, {
        name: 'search',
      }),
  },
  // Redis 히트 경로가 기본이나, 장 마감 후엔 캐시가 비어 3-DB-쿼리 폴백을
  // 탄다. 장중/장외 두 번 돌려 비교하는 게 이 프로파일의 핵심.
  price: {
    startRate: 50,
    steps: [250, 750, 1500, 2500],
    maxVUs: 800,
    req: () => safeGet(`/api/stocks/${pickHot(stockCodes)}/price`, { name: 'price' }),
  },
  // 최대 365행, 인덱스 있음, 캐시 없음. 응답 바디가 커서 직렬화 CPU도 같이 잰다.
  chart: {
    startRate: 20,
    steps: [100, 300, 600, 1000],
    maxVUs: 600,
    req: () => safeGet(`/api/stocks/${pick(stockCodes)}/chart?days=90`, { name: 'chart' }),
  },
  // 5초 TTL + synchronized refresh(외부 HTTP 8회)를 요청 스레드에서 그대로
  // 수행한다(MarketIndexCache). 외부 호출 자체는 TTL이 분당 최대 96회로
  // 캡핑하지만, 락 대기가 p99를 톱니로 만드는지를 본다. 상한을 낮게 잡는다.
  indices: {
    startRate: 10,
    steps: [50, 150, 300, 600],
    maxVUs: 600,
    req: () => safeGet('/api/market/indices', { name: 'indices' }),
  },
  // 10초 TTL 힙 캐시(TossMarketRankingCache). scope/sort는 절대 랜덤화하지
  // 않는다 - 조합을 늘리면 캐시 키가 늘어 Toss 레이트리밋 소모가 커진다
  // (docs/LOAD_TESTING.md 안전 규칙 참고). 다른 조합은 별도 짧은 런으로.
  ranking: {
    startRate: 20,
    steps: [100, 300, 700, 1200],
    maxVUs: 600,
    req: () =>
      safeGet('/api/market/ranking?scope=domestic&sort=gainers&limit=20', { name: 'ranking' }),
  },
  // 확정 N+1(다이제스트 행마다 풀엔티티 로딩 쿼리 1개). 커넥션당 쿼리 수가
  // 많아 HikariCP 대기가 가장 먼저 뜰 후보 중 하나.
  telegram: {
    startRate: 5,
    steps: [25, 75, 150, 300],
    maxVUs: 400,
    req: () => safeGet('/api/telegram-feed/digests?page=0&size=20', { name: 'telegram-feed' }),
  },
  // 잘 배치된(N+1 없는) 3~4쿼리 경로. telegram과 나란히 놓으면 N+1의
  // 실질 비용이 정량적으로 드러난다.
  videofeed: {
    startRate: 20,
    steps: [100, 300, 600, 1000],
    maxVUs: 500,
    req: () => safeGet('/api/video-feed/videos?page=0&size=20', { name: 'video-feed' }),
  },
};

const target = __ENV.TARGET || 'health';
const profile = PROFILES[target];
if (!profile) {
  throw new Error(`알 수 없는 TARGET=${target}. 가능한 값: ${Object.keys(PROFILES).join(', ')}`);
}

// 계단 하나당 2분(20초 안정화 + 2분 평탄부). 마지막에 30초 램프다운.
const STEADY = '2m';
const stages = profile.steps.flatMap((t) => [
  { target: t, duration: '20s' }, // 계단 오르기
  { target: t, duration: STEADY }, // 평탄부 - 이 구간의 값만 읽는다
]);
stages.push({ target: 0, duration: '30s' });

export const options = {
  discardResponseBodies: true, // 바디를 안 읽으면 부하생성기 CPU/메모리가 크게 준다
  scenarios: {
    [target]: {
      executor: 'ramping-arrival-rate',
      startRate: profile.startRate,
      timeUnit: '1s',
      // 사전 할당이 부족하면 "insufficient VUs" 경고가 뜨고, 그건 앱 한계가
      // 아니라 부하생성기 한계다 - 결과 해석을 망치므로 넉넉히 잡는다.
      preAllocatedVUs: Math.min(profile.maxVUs, 200),
      maxVUs: profile.maxVUs,
      stages,
      gracefulStop: '30s',
      tags: { endpoint: target },
    },
  },
  thresholds: abortThresholds({ p95Ms: 3000, failRate: 0.10 }),
};

export default function () {
  const res = profile.req();
  durByEndpoint.add(res.timings.duration, { endpoint: target });
  serverErrors.add(res.status >= 500, { endpoint: target });
}
