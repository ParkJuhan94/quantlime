# QuantLime — 부하테스트 가이드

> k6 기반 부하테스트 하네스(`load-test/`) 사용법. 하네스 자체의 안전장치
> (금지 경로 차단, 임계값=중단조건 등) 설계 근거는 각 파일 주석에 있고,
> 이 문서는 실행 절차와 판단 근거를 모은다.

## 목차

- [1. 배경](#1-배경)
- [2. 사전 준비](#2-사전-준비)
- [3. 실행 순서](#3-실행-순서)
- [4. k6 결과 출력 백엔드](#4-k6-결과-출력-백엔드)
- [5. 안전 규칙](#5-안전-규칙)
- [6. 결과 읽는 법](#6-결과-읽는-법)
- [7. 알려진 이슈 / 함정](#7-알려진-이슈--함정)

---

## 1. 배경

2026-08-17 세션에서 로컬 DB에 직접 쿼리를 실행해 측정한 결과, `GET
/api/dashboard/scores`의 실제 쿼리가 **116.7초** 걸리는 것을 포함해 심각한
병목 여러 개를 확인했다(상관 서브쿼리가 205만 번 실행됨). 이 하네스는
그 병목들을 재현 가능한 형태로 측정하고, 개선 전/후를 비교하기 위해
만들어졌다. 부하 생성기는 별도 EC2에 k6 + InfluxDB + Grafana로 구축돼
있다(앱 자체의 Prometheus/Grafana 관측 스택과는 별개).

**설계 원칙**: 고정 SLO를 미리 정하지 않고 breaking point를 먼저 찾는다
(`ramping-arrival-rate` open-loop 실행기 + threshold를 "중단 조건"으로
사용).

## 2. 사전 준비

1. `application.yml`에 `server.tomcat.mbeanregistry.enabled: true`가 있는지
   확인(이미 반영됨) - Tomcat 워커 스레드 포화를 관측하는 유일한 지표다.
2. 구독 시딩 함정 확인: `SUBSCRIPTION_BILLING_KEY_ENCRYPTION_KEY`가 대상
   환경에 설정돼 있으면 시딩한 더미 빌링키가 복호화 실패로 구독자의 모든
   프리미엄 요청을 500으로 만든다. 로컬은 보통 비어 있어 안전하지만 EC2는
   반드시 사전 확인할 것.
3. 설정 파일 준비:
   ```bash
   cp load-test/config/env.example load-test/config/env
   # 편집 후
   set -a && source load-test/config/env && set +a
   ```
4. 인증 토큰 확보 방식은 대상에 따라 다르다:
   - **로컬(8081)**: `seed.sh`가 `POST /dev/auth/token` 대신 오프라인
     서명을 그대로 쓴다(로컬/EC2 동일 경로로 통일 - 아래 참고).
   - **EC2**: `/dev/auth/token`을 쓸 수 없다(`DevController`가
     `@Profile("dev")`라 prod에 없고, 그 앞에서 nginx가 `/dev`를 SPA로
     떨어뜨려 405로 막는다). 인증이 완전 스테이트리스라
     (`JwtAuthenticationFilter`가 DB/Redis를 안 타고 토큰 클레임만 읽음)
     `JWT_SECRET`으로 오프라인 서명한 토큰을 쓴다 - 서버 코드 변경 0.
   ```bash
   set -a && source backend/.env && set +a   # JWT_SECRET 확보(로컬)
   # EC2 대상이면 EC2의 JWT_SECRET 값을 직접 export할 것
   ```
5. 시딩:
   ```bash
   make load-seed
   ```

## 3. 실행 순서

```bash
# 1) 스모크 - 여기서 실패하면 나머지는 볼 것도 없다
make load-smoke

# 2) 엔드포인트별 한계 탐색 (싼 것부터, 하네스 버그를 무해한 엔드포인트에서 먼저 잡기 위해)
for t in health chart videofeed price ranking search telegram indices; do
  ./load-test/run/run.sh endpoint-ramp TARGET=$t
  sleep 120   # 캐시/GC/CPU 크레딧 회복 대기
done

# 3) 혼합 여정 - 시스템 전체의 무릎(knee) 탐색
make load-journey

# 4) 소크 - 3단계에서 읽은 무릎의 60%
KNEE_RPS=<3단계 결과> SOAK_DURATION=45m ./load-test/run/run.sh soak

# 5) WebSocket (커넥션 용량 / 장중 팬아웃은 별도 2회)
./load-test/run/run.sh ws-stocks

# 6) 116초 쿼리 폭발반경 - 로컬(8081) 전용, 별도 opt-in 필요
I_UNDERSTAND_THIS_WILL_STALL_THE_APP=yes HTTP_TIMEOUT=180s \
  ./load-test/run/run.sh premium-scores SCORES_CONCURRENCY=3

# 7) 정리
make load-clean
```

각 단계의 근거(왜 이 실행기를 쓰는지, 왜 이 순서인지)는 해당 시나리오
파일(`load-test/scenarios/*.js`) 상단 주석에 있다.

## 4. k6 결과 출력 백엔드

기본값은 `--out json`(추가 인프라 불필요, 즉시 사용 가능)이다. 부하생성
EC2의 InfluxDB로 보내려면 먼저 버전을 확인한다 - 최신 k6는 내장
`--out influxdb`가 InfluxDB v1 전용이고, v2는 `xk6-output-influxdb` 확장
빌드가 필요하다.

```bash
# 프로브 A: k6 바이너리에 내장 influxdb 출력이 있는가
k6 run --out=__probe__ - <<< 'export default function () {}'
# 에러 메시지가 나열하는 출력 타입 목록에 influxdb가 있으면 내장, 없으면
# xk6-output-influxdb 확장이 필요하다.

# 프로브 B: InfluxDB 버전
curl -sI http://$INFLUX_HOST:8086/ping | grep -i x-influxdb-version   # v1
curl -s  http://$INFLUX_HOST:8086/health                               # v2
```

확인 후 `load-test/config/env`의 `K6_OUTPUT_MODE`를 `influxdb-v1` /
`influxdb-v2` / `json` 중 하나로 설정한다. v2는 `xk6 build --with
github.com/grafana/xk6-output-influxdb`로 만든 커스텀 k6 바이너리가
필요하다. 어느 쪽이든 `K6_INFLUXDB_TAGS_AS_FIELDS=url:string,name:string,error:string`가
자동으로 설정된다(`run/run.sh` 참고) - 안 하면 요청 URL(종목코드 2,596종
포함)이 태그가 돼 시계열이 수천 개로 폭발한다.

## 5. 안전 규칙

- **`/dev/**`, `/api/admin/**`, `/api/auth/**`, `/api/feedback`,
  `/actuator/**`, `/uploads/**` 절대 금지** - `lib/guard.js`가 물리적으로
  차단하고 `run/preflight.sh`가 시나리오에 GET 외 메서드나 `k6/http` 직접
  임포트가 없는지 매 실행 전 검사한다.
- **`GET /api/market/ranking`은 `scope`/`sort`를 절대 랜덤화하지 않는다** -
  TTL 10초 힙 캐시가 조합(scope×sort)별로 따로 있어, 조합을 늘리면 분당
  갱신 횟수가 배로 늘어 Toss 레이트리밋을 불필요하게 소모한다.
- **`GET /api/market/indices`는 TTL 5초가 캡을 걸어줘 상대적으로 안전** -
  부하가 아무리 커도 외부 호출은 분당 최대 96회(12회 refresh × 8콜)로
  제한된다.
- **로컬은 8081 포트만** - 사용자의 8080 라이브 세션을 건드리지 않는다.
  `lsof -ti:8081 | xargs -r kill -9`로 포트 기준 종료.
- **EC2 실행은 `TARGET_ENV=prod I_KNOW_THIS_IS_PROD=yes` 명시 필요**
  (`lib/config.js`가 강제).
- **`premium-scores.js`는 앱을 의도적으로 마비시킨다** -
  `I_UNDERSTAND_THIS_WILL_STALL_THE_APP=yes` 없이는 실행 자체가 거부된다.
  로컬 또는 점검 창에서만 실행할 것.

## 6. 결과 읽는 법

- **엔드포인트 한계**: `endpoint-ramp.js`의 각 평탄부에서 `(목표 rps, 실제
  rps, p95, p99, dropped_iterations)`를 기록한다. 실제 rps가 목표를 못
  따라가기 시작하는 첫 계단이 그 엔드포인트의 용량이다.
- **k6 지연 vs 서버 인지 지연 비교**가 진단의 핵심이다:
  - 둘이 같이 오른다 → 병목이 핸들러 내부(SQL/외부호출/CPU)
  - k6만 오르고 서버 쪽(`http_server_requests_seconds_bucket`)이 평평하다
    → 요청이 워커 스레드에 도달하기 전에 큐잉되고 있다(스레드풀/accept
    큐 포화)
- 1순위로 볼 지표: `hikaricp_connections_pending`(0보다 크면 DB 커넥션
  풀이 병목 확정), `hikaricp_connections_active`(10에 붙으면 포화).
  `monitoring/grafana/provisioning/dashboards/json/quantlime-loadtest.json`
  대시보드에 이미 패널로 있다.
- t3.medium은 버스터블 인스턴스라 CPU 크레딧이 바닥나면서 생기는 "가짜
  breaking point"를 조심할 것 - CloudWatch `CPUCreditBalance`를 실행
  전후로 기록.

## 7. 알려진 이슈 / 함정

- `ws-stocks.js`는 SockJS의 "raw WebSocket" 서브패스(`/ws/stocks/websocket`)에
  직접 붙는다 - 실제 브라우저는 SockJS 프레이밍(`o`, `a[...]`)을 쓰므로
  이 결과는 근사 상한치다.
- 장 마감 시간대(09:00~15:30 KST 외)엔 WebSocket 팬아웃 메시지가 0개인
  게 정상이다 - 릴레이 스케줄러가 장중에만 동작한다.
- 장 마감 후 `/api/stocks/{code}/price`는 Redis 캐시가 비어 있어 3-DB-쿼리
  폴백을 매번 탄다 - 장중/장외 두 번 돌려 비교할 가치가 있다.
