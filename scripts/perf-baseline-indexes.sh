#!/usr/bin/env bash
# Phase 2 DB 성능 개선(커밋 9065282)에서 추가된 인덱스 4개를 drop/restore한다.
#
# 배경: ddl-auto: update는 인덱스를 절대 DROP하지 않는다. 그래서 "before"
# 성능(예: B1 116.7초)을 재현하려고 fb571fc(하네스는 있고 개선은 전혀
# 반영 안 된 시점, ../quantlime-before worktree 참고)로 git checkout해도
# 로컬 DB에는 이 인덱스들이 그대로 남아있어 순수한 before 수치가 안 나온다.
# 측정 직전에 drop, 측정이 끝나면 반드시 restore로 되돌릴 것 - 개발 DB를
# 인덱스 없는 상태로 방치하면 다른 세션/현재 main의 정상 동작에도 영향을 준다.
#
#   ./scripts/perf-baseline-indexes.sh drop
#   ./scripts/perf-baseline-indexes.sh restore
#
# 대상 4개는 backend/core/.../{score/domain/Score,stock/domain/Stock,
# telegramfeed/domain/{TelegramDigest,TelegramPost}}.java의 @Table(indexes=...)와
# 정확히 일치시켰다 - 그 엔티티의 인덱스 선언이 나중에 바뀌면 이 스크립트도
# 같이 갱신해야 drop/restore가 실제 스키마와 어긋나지 않는다.
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3308}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-quantlime}"
DB_NAME="${DB_NAME:-quantlime}"

mode="${1:-}"
if [[ "$mode" != "drop" && "$mode" != "restore" ]]; then
  echo "사용법: $0 drop|restore" >&2
  exit 1
fi

mysql_exec() {
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" "$@"
}

# table|index_name|columns(ADD INDEX용, DROP엔 안 씀)
INDEXES=(
  "score|idx_score_composite_score|composite_score DESC"
  "stock|idx_stock_listing_status_market_type|listing_status, market_type"
  "telegram_digest|idx_telegram_digest_date|digest_date DESC"
  "telegram_post|idx_telegram_post_channel_status_published|channel_id, status, published_at"
)

for entry in "${INDEXES[@]}"; do
  IFS='|' read -r table index_name columns <<< "$entry"

  # 복합 인덱스는 information_schema.statistics에 컬럼 수만큼 행이 나오므로
  # seq_in_index=1(인덱스당 정확히 1행)로만 존재 여부를 판단한다.
  exists=$(mysql_exec -N -e \
    "SELECT COUNT(*) FROM information_schema.statistics \
     WHERE table_schema='$DB_NAME' AND table_name='$table' \
       AND index_name='$index_name' AND seq_in_index=1;")

  if [[ "$mode" == "drop" ]]; then
    if [[ "$exists" == "1" ]]; then
      echo "[drop] ${table}.${index_name}"
      mysql_exec -e "ALTER TABLE $table DROP INDEX $index_name;"
    else
      echo "[drop] ${table}.${index_name} - 이미 없음, 스킵"
    fi
  else
    if [[ "$exists" == "0" ]]; then
      echo "[restore] ${table}.${index_name} (${columns})"
      mysql_exec -e "ALTER TABLE $table ADD INDEX $index_name ($columns);"
    else
      echo "[restore] ${table}.${index_name} - 이미 있음, 스킵"
    fi
  fi
done

echo "[perf-baseline-indexes] $mode 완료"
