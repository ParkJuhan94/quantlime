// 모든 시나리오가 공유하는 설정. __ENV는 k6 init 컨텍스트에서만 읽을 수
// 있어 여기서 한 번만 파싱해 재사용한다.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
export const WS_BASE_URL = __ENV.WS_BASE_URL || BASE_URL.replace(/^http/, 'ws');
export const RUN_ID = __ENV.RUN_ID || `local-${Date.now()}`;

// 모든 요청에 붙는 태그 - InfluxDB/Prometheus에서 런 단위로 필터링할 때 쓴다.
// VU/iteration처럼 카디널리티가 큰 값은 절대 태그로 넣지 않는다(시계열 폭발).
export const RUN_TAGS = { run_id: RUN_ID, target_env: __ENV.TARGET_ENV || 'local' };

// 부하생성기가 앱보다 먼저 타임아웃으로 끊기지 않게 여유를 둔다.
// premium-scores.js처럼 응답이 30초를 넘는 시나리오는 개별적으로 늘려 쓴다.
export const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || '30s';

// prod(EC2) 대상 실행은 반드시 명시적 opt-in이 있어야 한다 - 실수로 라이브
// 서비스에 부하를 거는 사고를 막기 위함.
if (RUN_TAGS.target_env === 'prod' && __ENV.I_KNOW_THIS_IS_PROD !== 'yes') {
  throw new Error(
    'TARGET_ENV=prod인데 I_KNOW_THIS_IS_PROD=yes가 없다. ' +
    '실서비스에 부하를 걸기 전 반드시 명시적으로 확인할 것.'
  );
}
