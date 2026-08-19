#!/usr/bin/env bash
# 부하테스트 데이터 시딩 오케스트레이터.
#
#   ./load-test/seed/seed.sh              # 시딩(멱등, 여러 번 실행 안전)
#   ./load-test/seed/seed.sh --teardown   # 시드 데이터 전량 제거
#
# ⚠️ 이 스크립트는 기본적으로 로컬 docker-compose MySQL(quantlime-mysql,
# 포트 3308)만 지원한다. EC2 대상 시딩은 SQL 파일을 직접 그 DB에 실행하는
# 방식으로 대체할 것 - EC2 프로덕션 DB에 이 스크립트를 그대로 돌리기 전에
# 반드시 대상을 눈으로 확인할 것(전역 규칙 "DB/공유 상태를 건드리는 실험
# 전 확인 원칙").
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOAD_TEST_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DATA_DIR="$LOAD_TEST_DIR/data"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-quantlime-mysql}"
MYSQL_DB="${MYSQL_DATABASE:-quantlime}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-quantlime}"

mysql_exec() {
  docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" "$@"
}

confirm_target() {
  echo "[seed] 대상 컨테이너: $MYSQL_CONTAINER / DB: $MYSQL_DB"
  if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
    echo "[seed] 오류: 컨테이너 '$MYSQL_CONTAINER'가 떠 있지 않다. docker-compose up -d 먼저 실행할 것." >&2
    exit 1
  fi
}

if [[ "${1:-}" == "--teardown" ]]; then
  confirm_target
  echo "[seed] 시드 데이터 제거 중..."
  mysql_exec < "$SCRIPT_DIR/teardown-load-test-data.sql"
  echo "[seed] 제거 완료."
  exit 0
fi

confirm_target
mkdir -p "$DATA_DIR"

echo "[seed] 1/4 종목코드 풀 덤프..."
CODES_JSON=$(mysql_exec -N -e "$(cat "$SCRIPT_DIR/dump-stock-codes.sql")")
echo "$CODES_JSON" > "$DATA_DIR/stock-codes.json"
echo "[seed]    → $DATA_DIR/stock-codes.json ($(node -e "console.log(JSON.parse(require('fs').readFileSync('$DATA_DIR/stock-codes.json','utf8')).length)")개 종목)"

echo "[seed] 2/4 사용자/관심종목/구독/텔레그램 다이제스트 시딩..."
mysql_exec < "$SCRIPT_DIR/seed-load-test-data.sql"

echo "[seed] 3/4 토큰 발급 대상 사용자 목록 조회..."
USER_LIST_JSON=$(mysql_exec -N -e "
SET SESSION group_concat_max_len = 1000000;
SELECT JSON_ARRAYAGG(JSON_OBJECT('userId', u.user_id, 'premium', s.subscription_id IS NOT NULL))
FROM users u
LEFT JOIN subscription s ON s.user_id = u.user_id AND s.status = 'ACTIVE'
WHERE u.provider_id LIKE 'loadtest-%';
")
echo "$USER_LIST_JSON" > "$DATA_DIR/_user-list.json"

echo "[seed] 4/4 JWT 토큰 발급..."
if [[ -z "${JWT_SECRET:-}" ]]; then
  echo "[seed] 오류: JWT_SECRET 환경변수가 없다. backend/.env의 JWT_SECRET 값을 export할 것." >&2
  echo "[seed]   예) set -a && source backend/.env && set +a && ./load-test/seed/seed.sh" >&2
  exit 1
fi
node "$SCRIPT_DIR/mint-tokens.mjs" "$DATA_DIR/_user-list.json"
rm -f "$DATA_DIR/_user-list.json"

echo "[seed] 완료. make load-smoke 로 검증할 것."
