#!/usr/bin/env bash
# EC2 호스트에서 cron으로 1분마다 실행(scripts/install-cron.sh 참고).
# `docker stats` 결과를 node-exporter textfile collector 포맷으로 기록해
# 컨테이너별 메모리/CPU를 Prometheus -> Grafana로 보이게 한다.
#
# cAdvisor 대체용이다 - Docker 29(containerd 이미지 스토어가 기본)에서
# cAdvisor v0.49.1이 Docker 컨테이너를 하나도 등록하지 못해 container_*
# 지표에 name 라벨이 붙지 않는다(`failed to identify the read-write layer
# ID` 로그, containerd 네임스페이스 우회도 실패 - 2026-10-03 EC2에서 확인,
# 상세는 docs/CHANGELOG.md 참고). cAdvisor가 복구되면 이 스크립트는 지워도 된다.
#
# 지표명을 container_* 가 아니라 quantlime_container_* 로 둔 이유: cAdvisor가
# 복구됐을 때 같은 이름이 겹쳐 쿼리가 두 소스를 섞지 않게 하기 위함.
#
# 실패 시 set -e로 중단하고 기존 .prom을 그대로 둔다 - 갱신이 멈춘 낡은 값은
# node_textfile_mtime_seconds로 알아볼 수 있고, 빈 파일을 덮어써서 "컨테이너가
# 전부 사라졌다"로 보이는 것보다 낫다.
set -euo pipefail

QUANTLIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
METRIC_DIR="$QUANTLIME_DIR/monitoring/textfile-collector"
NAME_PREFIX="${NAME_PREFIX:-quantlime-prod-}"

# 테스트용 훅: 파일을 지정하면 docker stats 대신 그 내용을 입력으로 쓴다.
collect_stats() {
    if [ -n "${STATS_INPUT_FILE:-}" ]; then
        cat "$STATS_INPUT_FILE"
    else
        docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}'
    fi
}

mkdir -p "$METRIC_DIR"
METRIC_TMP="$METRIC_DIR/container_stats.prom.tmp"

# docker stats는 "352.3MiB / 1GiB" 같은 사람이 읽는 단위로 출력하므로 바이트로 환산한다.
collect_stats | awk -F'\t' -v prefix="$NAME_PREFIX" '
function tobytes(s,    n, u) {
    if (match(s, /^[0-9.]+/) == 0) return 0
    n = substr(s, RSTART, RLENGTH) + 0
    u = substr(s, RSTART + RLENGTH)
    if (u == "B") return n
    if (u == "kB" || u == "KB") return n * 1000
    if (u == "KiB") return n * 1024
    if (u == "MB") return n * 1000 * 1000
    if (u == "MiB") return n * 1024 * 1024
    if (u == "GB") return n * 1000 * 1000 * 1000
    if (u == "GiB") return n * 1024 * 1024 * 1024
    if (u == "TiB") return n * 1024 * 1024 * 1024 * 1024
    return 0
}
BEGIN {
    print "# HELP quantlime_container_memory_usage_bytes docker stats 기준 컨테이너 메모리 사용량(바이트)"
    print "# TYPE quantlime_container_memory_usage_bytes gauge"
}
index($1, prefix) == 1 {
    split($2, mem, " / ")
    cpu = $3; sub(/%/, "", cpu)
    usage[$1] = tobytes(mem[1])
    limit[$1] = tobytes(mem[2])
    cpus[$1] = cpu + 0
    names[++count] = $1
}
END {
    for (i = 1; i <= count; i++) printf "quantlime_container_memory_usage_bytes{name=\"%s\"} %.0f\n", names[i], usage[names[i]]
    print "# HELP quantlime_container_memory_limit_bytes 컨테이너 메모리 한도(바이트, mem_limit)"
    print "# TYPE quantlime_container_memory_limit_bytes gauge"
    for (i = 1; i <= count; i++) printf "quantlime_container_memory_limit_bytes{name=\"%s\"} %.0f\n", names[i], limit[names[i]]
    print "# HELP quantlime_container_cpu_percent docker stats 기준 CPU 사용률(코어 1개 = 100)"
    print "# TYPE quantlime_container_cpu_percent gauge"
    for (i = 1; i <= count; i++) printf "quantlime_container_cpu_percent{name=\"%s\"} %s\n", names[i], cpus[names[i]]
}
' > "$METRIC_TMP"

# 대상 컨테이너가 하나도 없으면(HELP/TYPE 줄만 있는 파일) 덮어쓰지 않는다 -
# docker 데몬 이상 같은 상황에서 정상 값을 지워버리지 않기 위함.
if ! grep -q '^quantlime_container_memory_usage_bytes{' "$METRIC_TMP"; then
    rm -f "$METRIC_TMP"
    echo "[report-container-metrics] '$NAME_PREFIX'로 시작하는 컨테이너가 없어 갱신하지 않습니다." >&2
    exit 1
fi

# 임시 파일에 쓴 뒤 rename - node-exporter가 반쯤 쓰인 파일을 읽지 않게 하기 위함
# (backup-mysql.sh와 동일한 공식 권장 패턴).
mv "$METRIC_TMP" "$METRIC_DIR/container_stats.prom"
