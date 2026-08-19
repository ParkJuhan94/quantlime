#!/usr/bin/env bash
# 공통 k6 러너. preflight 검사 → 출력 백엔드 선택 → 실행 → 결과 안내.
#
#   ./load-test/run/run.sh smoke
#   ./load-test/run/run.sh endpoint-ramp TARGET=search
#   ./load-test/run/run.sh journey
#   HTTP_TIMEOUT=180s I_UNDERSTAND_THIS_WILL_STALL_THE_APP=yes \
#     ./load-test/run/run.sh premium-scores SCORES_CONCURRENCY=3
#
# 환경변수는 load-test/config/env(gitignore 대상, env.example 참고)에서
# 미리 source해두거나 커맨드라인에서 직접 넘긴다. SCENARIO 뒤의 KEY=VALUE
# 인자는 모두 k6 -e로 전달된다(시나리오의 __ENV.KEY로 읽힘).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOAD_TEST_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

SCENARIO="${1:-}"
if [[ -z "$SCENARIO" ]]; then
  echo "사용법: $0 <scenario> [KEY=VALUE ...]" >&2
  echo "가능한 시나리오: smoke, endpoint-ramp, journey, premium-scores, ws-stocks, soak" >&2
  exit 1
fi
shift || true

SCENARIO_FILE="$LOAD_TEST_DIR/scenarios/$SCENARIO.js"
if [[ ! -f "$SCENARIO_FILE" ]]; then
  echo "[run] 시나리오 파일 없음: $SCENARIO_FILE" >&2
  exit 1
fi

"$SCRIPT_DIR/preflight.sh"

RUN_ID="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-${SCENARIO}}"
export RUN_ID
RESULTS_DIR="$LOAD_TEST_DIR/results/$RUN_ID"
mkdir -p "$RESULTS_DIR"

# KEY=VALUE 형태의 나머지 인자를 k6 -e 플래그로 변환
K6_ENV_ARGS=()
for arg in "$@"; do
  K6_ENV_ARGS+=(-e "$arg")
done

# 출력 백엔드 선택 - env에 K6_OUTPUT_MODE가 없으면 안전한 폴백(json)을 쓴다.
# influxdb-v1/v2를 쓰려면 load-test/config/env에서 미리 source해둘 것
# (§3.5 참고 - 버전을 추측하지 말고 k6 version / curl로 먼저 확인).
OUTPUT_MODE="${K6_OUTPUT_MODE:-json}"
K6_OUT_ARGS=(--summary-export "$RESULTS_DIR/summary.json")
case "$OUTPUT_MODE" in
  influxdb-v1)
    K6_OUT_ARGS+=(--out "influxdb=http://${INFLUX_HOST}:${INFLUX_PORT:-8086}/${INFLUX_DB:-k6}")
    export K6_INFLUXDB_TAGS_AS_FIELDS="url:string,name:string,error:string"
    ;;
  influxdb-v2)
    export K6_INFLUXDB_ORGANIZATION="${INFLUX_ORG}"
    export K6_INFLUXDB_BUCKET="${INFLUX_BUCKET}"
    export K6_INFLUXDB_TOKEN="${INFLUX_TOKEN}"
    export K6_INFLUXDB_TAGS_AS_FIELDS="url:string,name:string,error:string"
    K6_OUT_ARGS+=(--out "xk6-influxdb=http://${INFLUX_HOST}:${INFLUX_PORT:-8086}")
    ;;
  json)
    K6_OUT_ARGS+=(--out "json=$RESULTS_DIR/raw.json.gz")
    ;;
  *)
    echo "[run] 알 수 없는 K6_OUTPUT_MODE=$OUTPUT_MODE (influxdb-v1|influxdb-v2|json)" >&2
    exit 1
    ;;
esac

START_MS=$(($(date +%s%N) / 1000000))
echo "[run] 시나리오=$SCENARIO run_id=$RUN_ID output=$OUTPUT_MODE"
echo "[run] BASE_URL=${BASE_URL:-http://localhost:8081} (미설정 시 기본값)"

set +e
# macOS 기본 bash(3.2)는 빈 배열을 "${ARR[@]}"로 확장할 때 set -u 아래서
# unbound variable 에러를 낸다 - K6_ENV_ARGS가 비어 있을 수 있어(추가
# KEY=VALUE 인자가 없는 실행) ${ARR[@]:-} 형태로 방어한다.
( cd "$LOAD_TEST_DIR/scenarios" && k6 run "${K6_OUT_ARGS[@]}" ${K6_ENV_ARGS[@]:+"${K6_ENV_ARGS[@]}"} "$SCENARIO.js" )
RC=$?
set -e

END_MS=$(($(date +%s%N) / 1000000))
echo "{\"runId\":\"$RUN_ID\",\"scenario\":\"$SCENARIO\",\"startedAtMs\":$START_MS,\"endedAtMs\":$END_MS,\"exitCode\":$RC}" \
  > "$RESULTS_DIR/meta.json"

case $RC in
  0)
    echo "[run] 임계값 이내로 완주 - 아직 한계에 도달하지 않았다. 다음 계단으로 올릴 것."
    ;;
  99)
    echo "[run] 임계값(abortOnFail) 초과로 중단 - 한계점 도달. $RESULTS_DIR/summary.json의 마지막 평탄부를 읽을 것."
    ;;
  *)
    echo "[run] 비정상 종료(rc=$RC) - k6 자체 에러일 수 있다." >&2
    ;;
esac

if [[ -n "${APP_HOST:-}" ]]; then
  FROM=$((START_MS - 60000))
  TO=$((END_MS + 60000))
  echo "[run] 앱 Grafana(HTTP)      : http://${APP_HOST}:${APP_GRAFANA_PORT:-3002}/d/quantlime-http?from=$FROM&to=$TO"
  echo "[run] 앱 Grafana(부하테스트) : http://${APP_HOST}:${APP_GRAFANA_PORT:-3002}/d/quantlime-loadtest?from=$FROM&to=$TO"
fi

exit 0
