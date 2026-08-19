// 소크 테스트 - journey.js에서 찾은 무릎(knee)의 60% 처리율로 30~60분
// 유지한다. 힙 증가·GC 정체·market_sweep_duration 드리프트처럼 시간이
// 지나야 드러나는 문제, 그리고 t3.medium이 버스터블 인스턴스라 CPU
// 크레딧이 바닥나면서 생기는 "가짜 breaking point"를 구분하기 위한 것.
//
//   KNEE_RPS=<journey.js 결과> SOAK_DURATION=45m ./load-test/run/run.sh soak
//
// ⚠️ 실행 전후로 CloudWatch CPUCreditBalance(EC2)를 반드시 같이 기록할
// 것 - 소크 도중 크레딧이 떨어지고 있다면 그 이후의 성능 저하는 앱
// 문제가 아니라 인스턴스 타입 문제로 오진될 수 있다.
import { abortThresholds } from '../config/options.js';
export { home, browse, feeds, canary } from './journey.js';

// KNEE_RPS는 journey.js 실행 결과를 사람이 읽고 넣는 값이다 - 자동
// 추론하지 않는다(추론하면 그 자체가 측정 대상을 오염시킨다).
const KNEE = Number(__ENV.KNEE_RPS || 60);
const HOLD = __ENV.SOAK_DURATION || '45m';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    home: {
      executor: 'constant-arrival-rate',
      rate: Math.round(KNEE * 0.60),
      timeUnit: '1s',
      duration: HOLD,
      preAllocatedVUs: 100,
      maxVUs: 500,
      exec: 'home',
      tags: { journey: 'home' },
    },
    browse: {
      executor: 'constant-arrival-rate',
      rate: Math.round(KNEE * 0.24),
      timeUnit: '1s',
      duration: HOLD,
      preAllocatedVUs: 80,
      maxVUs: 400,
      exec: 'browse',
      tags: { journey: 'browse' },
    },
    feeds: {
      executor: 'constant-arrival-rate',
      rate: Math.round(KNEE * 0.12),
      timeUnit: '1s',
      duration: HOLD,
      preAllocatedVUs: 40,
      maxVUs: 200,
      exec: 'feeds',
      tags: { journey: 'feeds' },
    },
    canary: {
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '1s',
      duration: HOLD,
      preAllocatedVUs: 5,
      maxVUs: 20,
      exec: 'canary',
      tags: { journey: 'canary' },
    },
  },
  // 소크는 "느려지기 시작하면 바로 멈춘다"가 목적이라 램프 테스트보다
  // 훨씬 빡빡한 임계값을 쓴다.
  thresholds: abortThresholds({ p95Ms: 1500, failRate: 0.02, delay: '3m' }),
};
