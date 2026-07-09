#!/usr/bin/env bash
# EC2 호스트에서 cron으로 주기 실행(scripts/install-cron.sh 참고).
# CloudWatch Agent 없이 헬스체크/메모리/디스크 3개 지표만 커스텀
# 메트릭으로 올린다 - 이 정도 규모(단일 EC2, 저트래픽)에서는 이거면
# 충분하고, CloudWatch 커스텀 메트릭은 계정당 10개까지 상시 무료라
# 비용도 들지 않는다(docs/DEPLOYMENT.md 모니터링 섹션 참고).
#
# 에러 하나가 나머지 지표 전송까지 막지 않도록 set -e는 쓰지 않는다
# (예: /api/health가 일시적으로 죽어도 메모리/디스크는 계속 보고돼야
# CloudWatch 알람이 "무응답"과 "지표 자체가 안 옴"을 구분할 수 있음).
set -uo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
NAMESPACE="QuantLab"

put_metric() {
    local metric_name="$1"
    local value="$2"
    local unit="$3"
    aws cloudwatch put-metric-data \
        --region "$REGION" \
        --namespace "$NAMESPACE" \
        --metric-name "$metric_name" \
        --value "$value" \
        --unit "$unit" \
        || echo "[report-health-metric] $metric_name 전송 실패" >&2
}

# 1) 헬스체크: nginx를 거쳐 backend까지 실제로 응답하는지(컨테이너가
# 떠 있어도 backend가 죽어있으면 실패해야 하므로 EC2 자체가 아니라
# 반드시 애플리케이션 경로로 확인)
health_status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 http://localhost/api/health || echo "000")
if [ "$health_status" = "200" ]; then
    put_metric "HealthCheck" 1 "Count"
else
    put_metric "HealthCheck" 0 "Count"
fi

# 2) 메모리 사용률(%) - EC2 기본 제공 지표에는 없음(디스크/메모리는
# CloudWatch Agent 없이는 안 잡히는데, 우리는 free만 파싱해서 대체)
mem_percent=$(free | awk '/^Mem:/ {printf "%.1f", $3 * 100 / $2}')
if [ -n "$mem_percent" ]; then
    put_metric "MemoryUtilization" "$mem_percent" "Percent"
fi

# 3) 디스크 사용률(%) - 루트 볼륨 기준(도커 볼륨도 대부분 여기 위에 있음)
disk_percent=$(df --output=pcent / | tail -n1 | tr -dc '0-9')
if [ -n "$disk_percent" ]; then
    put_metric "DiskUtilization" "$disk_percent" "Percent"
fi
