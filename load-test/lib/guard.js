// 시나리오가 유일하게 써야 하는 HTTP 진입점. k6/http를 직접 import하는
// 시나리오는 run/preflight.sh가 정적 검사로 걸러낸다.
//
// 이 부하테스트 하네스는 GET 요청만 발행한다 - 관심종목 추가/피드 글쓰기/
// 결제 같은 쓰기 경로는 실제 사용자 데이터를 오염시키므로 스코프에서
// 완전히 제외했다.
import http from 'k6/http';
import { BASE_URL, HTTP_TIMEOUT, RUN_TAGS } from './config.js';

// 절대 부하를 걸면 안 되는 경로. 매치되면 요청을 보내지 않고 즉시 예외를
// 던져 시나리오를 죽인다 - "실수로 맞았는데 배치가 돌았다"를 원천 차단.
//   /dev/**       : 전종목 갱신/백테스트 등 수십 분짜리 배치 트리거
//                   (@Profile("dev")라 prod엔 없지만 로컬 8081엔 있다)
//   /api/admin/** : 피드 수집/요약 관리자 트리거 - 유튜브 쿼터/LLM 비용 소모
//   /api/auth/**  : reissue/logout이 Redis의 리프레시 토큰을 무효화함 -
//                   부하테스트용 토큰은 오프라인 서명이라 이 경로 자체가 불필요
//   /api/feedback : 실제 Slack 웹훅으로 메시지가 나감
//   /actuator/**  : Prometheus 전용 스크랩 대상 - 두들기면 측정 자체가 오염됨
//   /uploads/**   : 실사용자 업로드 파일을 디스크에서 서빙 - 용량 테스트 가치 없음
const DENY_PATTERNS = [
  /^\/dev\//,
  /^\/api\/admin\//,
  /^\/api\/auth\//,
  /^\/api\/feedback/,
  /^\/actuator\//,
  /^\/uploads\//,
];

function assertAllowed(path) {
  if (!path.startsWith('/')) {
    throw new Error(`[guard] 경로는 /로 시작해야 한다: ${path}`);
  }
  for (const pattern of DENY_PATTERNS) {
    if (pattern.test(path)) {
      throw new Error(`[guard] 금지 경로 호출 시도 - 실행을 중단한다: ${path}`);
    }
  }
}

// tags.name을 명시적으로 넘기지 않으면 URL 원문(종목코드 등)이 그대로
// 태그가 되어 결과 백엔드(InfluxDB/Prometheus)의 시계열이 폭발한다.
export function safeGet(path, tags = {}, headers = {}) {
  assertAllowed(path);
  if (!tags.name) {
    throw new Error(`[guard] safeGet 호출 시 tags.name을 반드시 지정할 것: ${path}`);
  }
  return http.get(`${BASE_URL}${path}`, {
    timeout: HTTP_TIMEOUT,
    headers,
    tags: { ...RUN_TAGS, ...tags },
  });
}
