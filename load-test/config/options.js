// 한계 탐색이 목적이라 threshold는 "합격/불합격 SLO"가 아니라 "여기서
// 멈춰라"는 안전장치다. 무릎(knee)을 지나 계단 한두 개는 더 올라가되,
// 앱이 죽거나 부하생성기가 의미 없는 타임아웃만 쌓기 전에 끊는다.
//
// delayAbortEval이 핵심이다 - 램프 초반은 표본이 적어 p95가 요동친다.
// 이게 없으면 테스트가 시작 몇 초 만에 스스로 중단돼버린다.
export function abortThresholds({ p95Ms = 3000, failRate = 0.10, delay = '1m' } = {}) {
  return {
    // 1) 실패율 - 5xx/타임아웃/커넥션 리셋 전부 포함. 이 이상이면 그 위
    //    계단의 숫자는 해석할 가치가 없다.
    http_req_failed: [
      { threshold: `rate<${failRate}`, abortOnFail: true, delayAbortEval: delay },
    ],

    // 2) 성공 응답의 p95 - expected_response:true로 좁혀야 에러의 빠른
    //    실패가 지연 통계를 낮춰 착시를 만드는 걸 막는다.
    'http_req_duration{expected_response:true}': [
      { threshold: `p(95)<${p95Ms}`, abortOnFail: true, delayAbortEval: delay },
    ],

    // 3) dropped_iterations - arrival-rate 실행기가 목표 rps를 못 채운 횟수.
    //    이게 쌓이기 시작하는 지점이 곧 무릎(knee)이다.
    dropped_iterations: [
      { threshold: 'count<5000', abortOnFail: true, delayAbortEval: delay },
    ],

    // 4) 5xx 전용 게이트 - 4xx(구독 403 등 정상 동작)는 여기서 분리한다.
    ql_server_error_rate: [
      { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: delay },
    ],

    // 5) 비중단 관찰용 - 응답 스키마가 깨졌는지만 본다.
    checks: ['rate>0.95'],
  };
}
