#!/usr/bin/env bash
# EC2에서 최초 1회(또는 스크립트 경로가 바뀔 때) 실행 - 헬스/리소스
# 지표 전송(5분마다)과 MySQL 백업(매일 새벽 3시, 16시 배치와 장중을
# 피한 시간대)을 crontab에 등록한다.
#
# 멱등성: 이미 등록된 줄은 마커 주석(quantlab-report-health-metric /
# quantlab-backup-mysql)으로 걸러 중복 추가하지 않는다 - 이 스크립트를
# 재배포 때마다 실수로 다시 돌려도 크론탭에 같은 작업이 여러 줄 쌓이지 않음.
set -euo pipefail

QUANTLAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$QUANTLAB_DIR/logs"
mkdir -p "$LOG_DIR"

HEALTH_MARKER="# quantlab-report-health-metric"
BACKUP_MARKER="# quantlab-backup-mysql"

HEALTH_LINE="*/5 * * * * $QUANTLAB_DIR/scripts/report-health-metric.sh >> $LOG_DIR/health-metric.log 2>&1 $HEALTH_MARKER"
BACKUP_LINE="0 3 * * * $QUANTLAB_DIR/scripts/backup-mysql.sh >> $LOG_DIR/backup-mysql.log 2>&1 $BACKUP_MARKER"

current_crontab="$(crontab -l 2>/dev/null || true)"

new_crontab="$current_crontab"

if ! grep -qF "$HEALTH_MARKER" <<< "$current_crontab"; then
    new_crontab="$new_crontab
$HEALTH_LINE"
    echo "[install-cron] 헬스/리소스 지표 전송(5분 간격) 등록"
else
    echo "[install-cron] 헬스/리소스 지표 전송 - 이미 등록됨, 건너뜀"
fi

if ! grep -qF "$BACKUP_MARKER" <<< "$current_crontab"; then
    new_crontab="$new_crontab
$BACKUP_LINE"
    echo "[install-cron] MySQL 백업(매일 03:00) 등록"
else
    echo "[install-cron] MySQL 백업 - 이미 등록됨, 건너뜀"
fi

# 앞뒤 빈 줄 정리 후 반영
echo "$new_crontab" | sed '/^$/d' | crontab -

echo "[install-cron] 완료. 현재 crontab:"
crontab -l
