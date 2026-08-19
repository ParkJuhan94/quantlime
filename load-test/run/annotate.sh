#!/usr/bin/env bash
# 부하 테스트 구간을 앱 자체 Grafana(:3002, k6용 Grafana와 별개)에
# 어노테이션으로 남긴다 - 두 Grafana(부하생성 EC2 vs 앱 EC2)를 시간축으로
# 맞춰볼 때 기준점이 된다.
#
#   ./load-test/run/annotate.sh "$RUN_ID" "$SCENARIO" "$START_MS" "$END_MS"
#
# GRAFANA_API_TOKEN이 없으면 조용히 스킵한다(선택 기능이라 필수 아님).
set -euo pipefail

RUN_ID="${1:?run_id 필요}"
SCENARIO="${2:?scenario 필요}"
START_MS="${3:?start_ms 필요}"
END_MS="${4:?end_ms 필요}"

if [[ -z "${GRAFANA_API_TOKEN:-}" ]]; then
  echo "[annotate] GRAFANA_API_TOKEN 미설정 - 어노테이션을 건너뛴다."
  exit 0
fi

APP_HOST="${APP_HOST:-localhost}"
APP_GRAFANA_PORT="${APP_GRAFANA_PORT:-3002}"

curl -sS -XPOST "http://${APP_HOST}:${APP_GRAFANA_PORT}/api/annotations" \
  -H "Authorization: Bearer $GRAFANA_API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"time\":$START_MS,\"timeEnd\":$END_MS,\"tags\":[\"k6\",\"$SCENARIO\"],\"text\":\"k6 $SCENARIO run_id=$RUN_ID\"}" \
  && echo "[annotate] 어노테이션 등록 완료."
