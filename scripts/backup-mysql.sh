#!/usr/bin/env bash
# EC2 호스트에서 cron으로 매일 실행(scripts/install-cron.sh 참고).
# quantlime-prod-mysql 컨테이너 안에서 mysqldump를 돌려 대상 DB만
# 백업(--all-databases 아님 - mysql 시스템 스키마까지 백업할 이유 없음)
# 하고 gzip 압축 후 S3에 올린다.
#
# Redis는 백업 대상이 아니다 - PriceCacheStore가 담는 건 시세 스냅샷/
# 스코어 캐시뿐이라 유실돼도 다음 폴링 틱/배치에서 다시 채워지는
# 파생 데이터이고, 유일한 진실 소스는 MySQL뿐이다.
#
# 백업 실패는 조용히 넘어가면 안 되는 종류의 실패라 set -e로 즉시 중단한다
# (report-health-metric.sh와 반대 - 그쪽은 지표 하나 실패해도 나머지는
# 계속 보고해야 하지만, 여긴 중간에 실패하면 반쪽짜리 백업을 남기지 않는
# 게 낫다).
set -euo pipefail

QUANTLIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$QUANTLIME_DIR/.env.prod"
BACKUP_DIR="$QUANTLIME_DIR/backups"
CONTAINER_NAME="quantlime-prod-mysql"
LOCAL_RETENTION_COUNT=3

if [ ! -f "$ENV_FILE" ]; then
    echo "[backup-mysql] $ENV_FILE 이 없습니다." >&2
    exit 1
fi

# .env.prod의 KEY=VALUE 정의를 전부 환경변수로 로드(DB_PASSWORD,
# MYSQL_DATABASE, BACKUP_S3_BUCKET, AWS_REGION 등)
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_PASSWORD:?DB_PASSWORD가 .env.prod에 없습니다}"
: "${BACKUP_S3_BUCKET:?BACKUP_S3_BUCKET이 .env.prod에 없습니다}"
MYSQL_DATABASE="${MYSQL_DATABASE:-quantlime}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
FILENAME="quantlime-mysql-${TIMESTAMP}.sql.gz"
FILEPATH="$BACKUP_DIR/$FILENAME"

echo "[backup-mysql] 덤프 시작: db=$MYSQL_DATABASE"
# -p 대신 MYSQL_PWD 환경변수로 넘겨 `docker exec` 명령 인자에 비밀번호가
# 그대로 노출되지 않게 한다(ps aux 등으로 인자는 보일 수 있어도 env는 안 보임)
docker exec -e MYSQL_PWD="$DB_PASSWORD" "$CONTAINER_NAME" \
    mysqldump -u root --single-transaction "$MYSQL_DATABASE" \
    | gzip > "$FILEPATH"

echo "[backup-mysql] S3 업로드: s3://$BACKUP_S3_BUCKET/mysql/$FILENAME"
aws s3 cp "$FILEPATH" "s3://$BACKUP_S3_BUCKET/mysql/$FILENAME" --region "$AWS_REGION"

# 로컬엔 최근 n개만 남긴다 - 장기 보존은 S3 라이프사이클 규칙에 위임
# (docs/DEPLOYMENT.md 참고). EC2 루트 볼륨을 백업 파일로 채우지 않기 위함.
ls -1t "$BACKUP_DIR"/quantlime-mysql-*.sql.gz 2>/dev/null \
    | tail -n +$((LOCAL_RETENTION_COUNT + 1)) \
    | xargs -r rm -f

echo "[backup-mysql] 완료: $FILENAME"
