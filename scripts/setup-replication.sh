#!/usr/bin/env bash
# 로컬 MySQL primary-replica 복제를 처음부터 재현 가능하게 구성한다.
# 이미 mysql_data 볼륨에 데이터(1GB+)가 있는 상태에서 시작하므로, 빈
# replica에 곧바로 GTID 복제를 걸 수 없다(과거 binlog가 없어 실패) -
# 대신 GTID를 켠 뒤 뜬 덤프 하나로 replica를 초기화하고, 그 덤프에
# mysqldump가 자동으로 남기는 GTID_PURGED로 복제 시작점을 맞춘다.
#
# 사용법: DB_PASSWORD=quantlime ./scripts/setup-replication.sh
#
# 전제: docker-compose.yml의 mysql 서비스가 이미 GTID 활성 설정으로 떠
# 있어야 한다(docker-compose up -d mysql로 재기동 필요 시 먼저 수행).
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

DB_PASSWORD="${DB_PASSWORD:-quantlime}"
MYSQL_DATABASE="${MYSQL_DATABASE:-quantlime}"
REPL_PASSWORD="${REPL_PASSWORD:-replpass}"
PRIMARY_CONTAINER="quantlime-mysql"
REPLICA_CONTAINER="quantlime-mysql-replica"
DUMP_FILE="/tmp/quantlime-replica-seed.sql"

echo "[setup-replication] 1/6 primary GTID 상태 확인..."
GTID_MODE=$(docker exec "$PRIMARY_CONTAINER" mysql -uroot -p"$DB_PASSWORD" -N -e "SHOW VARIABLES LIKE 'gtid_mode';" | awk '{print $2}')
if [[ "$GTID_MODE" != "ON" ]]; then
  echo "[setup-replication] primary의 gtid_mode=$GTID_MODE - docker-compose.yml에 GTID 플래그를 반영하고" >&2
  echo "  'docker-compose up -d mysql'로 재기동한 뒤 다시 실행할 것." >&2
  exit 1
fi

echo "[setup-replication] 2/6 복제 계정 생성(존재하면 비밀번호만 갱신)..."
docker exec "$PRIMARY_CONTAINER" mysql -uroot -p"$DB_PASSWORD" -e "
  CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED WITH mysql_native_password BY '$REPL_PASSWORD';
  ALTER USER 'repl'@'%' IDENTIFIED WITH mysql_native_password BY '$REPL_PASSWORD';
  GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
  FLUSH PRIVILEGES;
"

echo "[setup-replication] 3/6 replica 컨테이너 기동(빈 데이터 디렉터리)..."
docker-compose -f docker-compose.yml -f docker-compose.replica.yml up -d mysql-replica
echo "[setup-replication]     헬스체크 대기..."
for i in $(seq 1 30); do
  status=$(docker inspect --format='{{.State.Health.Status}}' "$REPLICA_CONTAINER" 2>/dev/null || echo "starting")
  [[ "$status" == "healthy" ]] && break
  sleep 2
done
if [[ "$status" != "healthy" ]]; then
  echo "[setup-replication] replica 컨테이너가 healthy 상태가 되지 않았다(status=$status)" >&2
  exit 1
fi

echo "[setup-replication] 4/6 primary 덤프(GTID_PURGED 자동 포함)..."
docker exec "$PRIMARY_CONTAINER" mysqldump -uroot -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers \
  --databases "$MYSQL_DATABASE" > "$DUMP_FILE"
echo "[setup-replication]     덤프 크기: $(du -h "$DUMP_FILE" | cut -f1)"

echo "[setup-replication] 5/6 replica에 덤프 적재..."
docker exec -i "$REPLICA_CONTAINER" mysql -uroot -p"$DB_PASSWORD" < "$DUMP_FILE"
rm -f "$DUMP_FILE"

echo "[setup-replication] 6/6 복제 시작(SOURCE_AUTO_POSITION=1)..."
docker exec "$REPLICA_CONTAINER" mysql -uroot -p"$DB_PASSWORD" -e "
  CHANGE REPLICATION SOURCE TO
    SOURCE_HOST='mysql',
    SOURCE_PORT=3306,
    SOURCE_USER='repl',
    SOURCE_PASSWORD='$REPL_PASSWORD',
    SOURCE_AUTO_POSITION=1;
  START REPLICA;
"

sleep 3
echo "[setup-replication] 7/7 복제 정상 확인 후 read-only 활성화(SET PERSIST - 컨테이너 재시작에도 유지)..."
docker exec "$REPLICA_CONTAINER" mysql -uroot -p"$DB_PASSWORD" -e "
  SET PERSIST read_only=ON;
  SET PERSIST super_read_only=ON;
"

echo "[setup-replication] 완료. 상태 확인:"
docker exec "$REPLICA_CONTAINER" mysql -uroot -p"$DB_PASSWORD" -e "SHOW REPLICA STATUS\G" | grep -E "Replica_IO_Running|Replica_SQL_Running|Seconds_Behind_Source|Last_IO_Error|Last_SQL_Error"
docker exec "$REPLICA_CONTAINER" mysql -uroot -p"$DB_PASSWORD" -N -e "SHOW VARIABLES LIKE 'super_read_only';"
