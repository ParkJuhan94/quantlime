#!/usr/bin/env bash
# EC2에서 최초 1회(또는 스크립트 경로가 바뀔 때) 실행 - MySQL/업로드 백업을
# crontab에 등록한다. 의도한 실행 시각은 KST 기준 새벽 3시/3시 15분(16시
# 배치와 장중을 피한 시간대)이지만, EC2 호스트 자체가 UTC(Etc/UTC)로
# 떠 있어 시각은 UTC로 환산해 적는다 - crontab 시각은 항상 호스트
# 로컬시간 기준이기 때문(cron(8)). KST가 UTC+9라 "매일 03:00/03:15 KST"는
# "전날 18:00/18:15 UTC"와 같다 - 매일 도는 배치라 날짜가 하루 당겨지는 것
# 자체는 문제가 안 된다(2026-09-10 발견 - 최초 등록 이후 KST 시각을 그대로
# 적어놔서 실제로는 03:00/03:15 UTC = 정오 무렵 KST, 즉 장중에 돌고 있었다.
# `date`/`timedatectl`로 호스트 타임존 확인 없이 KST 습관대로 적은 게
# 원인 - docs/00-sre/SRE.md "백업" 절 참고).
#
# 헬스/리소스 지표는 더 이상 여기서 다루지 않는다 - CloudWatch 커스텀
# 메트릭 대신 PLG 관측성 스택(docker-compose.monitoring.yml,
# node-exporter/cAdvisor + Prometheus + Alertmanager)으로 일원화했다
# (docs/DEPLOYMENT.md 참고).
#
# 멱등성: 마커 주석(quantlime-backup-mysql 등)으로 기존에 등록된 줄을 찾아
# 항상 이 스크립트가 정의한 최신 내용으로 덮어쓴다 - "마커가 있으면
# 건너뛴다"였던 이전 방식은 시각/경로가 바뀌어도 재실행 시 갱신되지 않는
# 별개 버그였다(위 UTC 전환이 실제로 그 버그에 걸려 발견됨). 매번
# 지우고-다시-쓰는 방식이라 여러 번 실행해도 중복 등록되지 않는다.
set -euo pipefail

QUANTLIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$QUANTLIME_DIR/logs"
mkdir -p "$LOG_DIR"

BACKUP_MARKER="# quantlime-backup-mysql"
UPLOADS_MARKER="# quantlime-backup-uploads"

# 18:00/18:15 UTC = 03:00/03:15 KST(다음날)
BACKUP_LINE="0 18 * * * $QUANTLIME_DIR/scripts/backup-mysql.sh >> $LOG_DIR/backup-mysql.log 2>&1 $BACKUP_MARKER"
UPLOADS_LINE="15 18 * * * $QUANTLIME_DIR/scripts/backup-uploads.sh >> $LOG_DIR/backup-uploads.log 2>&1 $UPLOADS_MARKER"

current_crontab="$(crontab -l 2>/dev/null || true)"

# 기존 마커 줄을 전부 제거한 뒤 현재 정의로 다시 추가 - 시각/경로 변경이
# 재실행만으로 반영되도록 한다.
new_crontab="$(grep -vF "$BACKUP_MARKER" <<< "$current_crontab" | grep -vF "$UPLOADS_MARKER" || true)"
new_crontab="$new_crontab
$BACKUP_LINE
$UPLOADS_LINE"

# 앞뒤 빈 줄 정리 후 반영
echo "$new_crontab" | sed '/^$/d' | crontab -

echo "[install-cron] MySQL 백업(매일 03:00 KST = 18:00 UTC) 등록/갱신"
echo "[install-cron] 업로드 이미지 백업(매일 03:15 KST = 18:15 UTC) 등록/갱신"
echo "[install-cron] 완료. 현재 crontab:"
crontab -l
