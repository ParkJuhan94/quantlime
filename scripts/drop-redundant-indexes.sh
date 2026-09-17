#!/usr/bin/env bash
# 2026-09 성능 감사에서 발견한 중복 인덱스 7개를 운영 DB에서 제거한다.
#
# 배경: ddl-auto: update는 인덱스를 절대 DROP하지 않는다(추가만 함) -
# 그래서 엔티티 @Table(indexes=...)에서 @Index 선언을 지워도 이미 배포된
# DB에는 실제 인덱스가 그대로 남는다. 이 스크립트가 그 간극을 메운다.
#
# 대상 7개는 전부 같은 안티패턴이다 - uniqueConstraints와 @Index가 같은
# 컬럼 조합을 중복 선언해, uk_* 인덱스가 이미 커버하는 조회를 위해
# idx_* 인덱스가 별도로 존재했다. MySQL은 오름차순 인덱스를 역방향으로도
# 스캔할 수 있어(EXPLAIN상 "Index lookup ... (reverse)") 단일 컬럼 정렬
# (findTopByStockCodeOrderByTradeDateDesc류)에는 DESC 인덱스가 애초에
# 불필요했다 - 로컬 dev DB 실측: 258MB, performance_schema 2.3일간 읽기
# 0~3회. 각 엔티티 파일의 @Table 선언 옆에 남긴 주석이 이 근거의 원본이다.
#
# perf-baseline-indexes.sh와 동일하게 information_schema.statistics로 존재
# 여부를 확인한 뒤에만 DROP한다(MySQL은 DROP INDEX IF EXISTS를 지원하지
# 않음) - 재실행해도 안전(idempotent)하고, 배포 순서가 꼬여 인덱스가 아직
# 없어도 에러 없이 스킵된다.
#
#   ./scripts/drop-redundant-indexes.sql.sh          # 대상 목록만 보고(dry-run)
#   ./scripts/drop-redundant-indexes.sql.sh --apply  # 실제 DROP 실행
#
# 실행 후 docs/00-sre/DEPLOYMENT.md의 "인덱스 정리 적용 이력" 표에 적용
# 일자를 기록할 것.
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3308}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-quantlime}"
DB_NAME="${DB_NAME:-quantlime}"

apply=false
if [[ "${1:-}" == "--apply" ]]; then
  apply=true
fi

mysql_exec() {
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" "$@"
}

# table|index_name
INDEXES=(
  "score|idx_score_stock_date"
  "overseas_daily_price|idx_overseas_daily_price_stock_date"
  "domestic_daily_price|idx_daily_price_stock_date"
  "backtest_daily_score|idx_backtest_daily_score_stock_version"
  "benchmark_index|idx_benchmark_index_code_date"
  "domestic_regular_close_price|idx_domestic_regular_close_stock_date"
  "investor_trading|idx_investor_trading_market_interval_date"
)

if [[ "$apply" == false ]]; then
  echo "[dry-run] --apply 없이 실행됨 - 실제로는 아무것도 지우지 않음. 대상:"
fi

for entry in "${INDEXES[@]}"; do
  IFS='|' read -r table index_name <<< "$entry"

  # 복합 인덱스는 information_schema.statistics에 컬럼 수만큼 행이 나오므로
  # seq_in_index=1(인덱스당 정확히 1행)로만 존재 여부를 판단한다.
  exists=$(mysql_exec -N -e \
    "SELECT COUNT(*) FROM information_schema.statistics \
     WHERE table_schema='$DB_NAME' AND table_name='$table' \
       AND index_name='$index_name' AND seq_in_index=1;")

  if [[ "$exists" != "1" ]]; then
    echo "[skip] ${table}.${index_name} - 이미 없음"
    continue
  fi

  if [[ "$apply" == true ]]; then
    echo "[drop] ${table}.${index_name}"
    mysql_exec -e "ALTER TABLE $table DROP INDEX $index_name;"
  else
    echo "  - ${table}.${index_name}"
  fi
done

if [[ "$apply" == true ]]; then
  echo "[drop-redundant-indexes] 완료"
fi
