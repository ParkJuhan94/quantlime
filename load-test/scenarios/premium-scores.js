// B1(GET /api/dashboard/scores?watchlistOnly=false)의 폭발반경 실험.
//
// 이건 용량 테스트가 아니다 - 이번 세션에 로컬 DB에서 직접 측정한 결과,
// 이 쿼리 하나가 116.7초 걸린다(상관 서브쿼리가 205만 번 실행됨,
// plan 문서 §1 B1 참고). HikariCP maximum-pool-size가 10이므로, 동시
// 요청 10개만 있어도 커넥션 풀 전체가 100초 넘게 고갈되고 다른 모든
// 엔드포인트가 "connection is not available, request timed out after
// 30000ms"로 무너진다.
//
// 여기서 측정하려는 건 "몇 개의 동시 느린 쿼리가 앱 전체를 마비시키는가"와
// "끝난 뒤 회복 시간"이다. SCORES_CONCURRENCY를 1→3→6→10→12로 올려가며
// 별도 실행으로 스윕한다.
//
//   HTTP_TIMEOUT=180s I_UNDERSTAND_THIS_WILL_STALL_THE_APP=yes \
//     ./load-test/run/run.sh premium-scores SCORES_CONCURRENCY=3
//
// ⚠️ Phase 2(B1 쿼리 재작성)를 적용하기 전에는 로컬(8081)에서만 실행할 것.
// 재작성 후(116.7s → 0.82s 확인됨) 다시 돌려 실제로 개선됐는지 검증하는
// 용도로도 그대로 재사용한다.
import { safeGet } from '../lib/guard.js';
import { premiumUserForVu, bearer } from '../lib/auth.js';

if (__ENV.I_UNDERSTAND_THIS_WILL_STALL_THE_APP !== 'yes') {
  throw new Error(
    'premium-scores.js는 I_UNDERSTAND_THIS_WILL_STALL_THE_APP=yes 없이는 실행할 수 없다. ' +
    '이 시나리오는 의도적으로 앱을 마비시킨다.'
  );
}

const CONCURRENCY = Number(__ENV.SCORES_CONCURRENCY || 3);

export const options = {
  discardResponseBodies: true,
  scenarios: {
    // 느린 쿼리 자체 - VU 수 = 동시 실행 수. arrival-rate가 아니라 고정
    // 동시성으로 가야 "커넥션 N개를 실제로 붙잡았을 때"를 정확히 재현한다.
    slow: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: '6m',
      exec: 'slowScores',
      tags: { journey: 'slow-scores' },
    },
    // 폭발반경 측정 - 위가 도는 동안 정상 요청이 얼마나 느려지는가.
    canary: {
      executor: 'constant-arrival-rate',
      rate: 2,
      timeUnit: '1s',
      duration: '8m',
      preAllocatedVUs: 20,
      maxVUs: 100,
      exec: 'canary',
      startTime: '0s',
      tags: { journey: 'canary' },
    },
  },
  // abortOnFail을 쓰지 않는다 - 앱이 무너지는 과정 자체를 관찰하려는
  // 테스트라 중단시키면 관측이 안 된다. duration으로만 상한을 건다.
  thresholds: {
    'http_req_duration{journey:canary}': ['p(95)<1000'], // 실패해도 중단 안 함(관찰용)
  },
};

export function slowScores() {
  const user = premiumUserForVu();
  safeGet(
    '/api/dashboard/scores?watchlistOnly=false&limit=50',
    { name: 'scores-full' },
    bearer(user)
  );
}

export function canary() {
  safeGet('/api/health', { name: 'canary-health' });
}
