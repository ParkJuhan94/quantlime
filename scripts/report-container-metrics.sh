#!/usr/bin/env bash
# EC2 호스트에서 cron으로 1분마다 실행(scripts/install-cron.sh 참고).
# `docker stats`/`docker inspect` 결과를 node-exporter textfile collector 포맷으로 기록해
# 컨테이너별 메모리/CPU/생존/기동 시각을 Prometheus -> Grafana·알림에서 쓰게 한다.
#
# cAdvisor를 대체한다 - Docker 29(containerd 이미지 스토어가 기본)에서 cAdvisor
# v0.49.1이 Docker 컨테이너를 하나도 등록하지 못해 container_* 지표에 name 라벨이
# 붙지 않았고(`failed to identify the read-write layer ID` 로그, containerd 네임스페이스
# 우회도 실패 - 2026-10-03 EC2에서 확인), 그 지표에 의존하던 FrontendDown/RedpandaDown/
# ContainerRestartLoop 알림이 조용히 죽어 있었다. 그래서 cAdvisor 자체를 제거하고
# 같은 신호를 이 스크립트의 지표로 다시 만들었다(알림은 monitoring/prometheus/rules/alerts.yml).
#
# 지표명을 container_* 가 아니라 quantlime_container_* 로 둔 이유: 같은 이름을 쓰던 옛
# cAdvisor 지표와 의미(스냅샷 주기 1분, docker stats 기준)가 달라 쿼리가 섞이지 않게 하기 위함.
#
# 실패 시 set -e로 중단하고 기존 .prom을 그대로 둔다 - 갱신이 멈춘 낡은 값은
# quantlime_container_metrics_last_success_timestamp_seconds(ContainerMetricsStale 알림)로
# 알아볼 수 있고, 빈 파일을 덮어써서 "컨테이너가 전부 사라졌다"로 보이는 것보다 낫다.
set -euo pipefail

QUANTLIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
METRIC_DIR="$QUANTLIME_DIR/monitoring/textfile-collector"
NAME_PREFIX="${NAME_PREFIX:-quantlime-prod-}"

# 테스트용 훅: 파일을 지정하면 docker 대신 그 내용을 입력으로 쓴다.
collect_stats() {
    if [ -n "${STATS_INPUT_FILE:-}" ]; then
        cat "$STATS_INPUT_FILE"
    else
        docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}'
    fi
}

# "이름 상태 시작시각" 한 줄씩. 정지된 컨테이너도 포함해야 up=0을 알릴 수 있다
# (docker stats는 실행 중인 컨테이너만 보여준다).
collect_inspect() {
    if [ -n "${INSPECT_INPUT_FILE:-}" ]; then
        cat "$INSPECT_INPUT_FILE"
        return
    fi
    local ids
    ids="$(docker ps -aq)"
    [ -n "$ids" ] || return 0
    # shellcheck disable=SC2086
    docker inspect --format '{{.Name}} {{.State.Status}} {{.State.StartedAt}}' $ids
}

to_epoch() {
    case "$1" in
        0001-*|"") echo 0 ;; # 한 번도 시작되지 않은 컨테이너
        *) date -d "$1" +%s 2>/dev/null || echo 0 ;;
    esac
}

mkdir -p "$METRIC_DIR"
METRIC_TMP="$METRIC_DIR/container_stats.prom.tmp"
INSPECT_TMP="$METRIC_DIR/container_inspect.tmp"
trap 'rm -f "$INSPECT_TMP"' EXIT

# 이름의 선행 "/"를 떼고 시작 시각을 epoch 초로 바꿔 둔다
collect_inspect | while read -r name status started; do
    echo "${name#/} $status $(to_epoch "$started")"
done > "$INSPECT_TMP"

# docker stats는 "352.3MiB / 1GiB" 같은 사람이 읽는 단위로 출력하므로 바이트로 환산한다.
{
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
    '
    # up: 실행 중이면 1, 그 외(exited/restarting/created 등)는 0. FrontendDown/RedpandaDown이 쓴다.
    # start_time: ContainerRestartLoop이 changes()로 재시작 횟수를 센다(autoheal의 docker restart도
    # StartedAt이 바뀌므로 잡힌다 - 옛 cAdvisor container_start_time_seconds와 같은 의미).
    awk -v prefix="$NAME_PREFIX" '
    index($1, prefix) == 1 { names[++count] = $1; status[$1] = $2; started[$1] = $3 }
    END {
        print "# HELP quantlime_container_up 컨테이너가 실행 중이면 1, 아니면 0(정지된 컨테이너 포함)"
        print "# TYPE quantlime_container_up gauge"
        for (i = 1; i <= count; i++) printf "quantlime_container_up{name=\"%s\"} %d\n", names[i], (status[names[i]] == "running") ? 1 : 0
        print "# HELP quantlime_container_start_time_seconds 컨테이너의 마지막 시작 시각(유닉스 타임스탬프)"
        print "# TYPE quantlime_container_start_time_seconds gauge"
        for (i = 1; i <= count; i++) printf "quantlime_container_start_time_seconds{name=\"%s\"} %s\n", names[i], started[names[i]]
    }
    ' "$INSPECT_TMP"
    echo "# HELP quantlime_container_metrics_last_success_timestamp_seconds 이 스크립트가 마지막으로 지표를 갱신한 유닉스 타임스탬프"
    echo "# TYPE quantlime_container_metrics_last_success_timestamp_seconds gauge"
    echo "quantlime_container_metrics_last_success_timestamp_seconds $(date +%s)"
} > "$METRIC_TMP"

# 대상 컨테이너가 하나도 없으면(docker 데몬 이상 등) 덮어쓰지 않는다 -
# 정상 값을 지워버리지 않기 위함. 이 경우 last_success가 갱신되지 않아 Stale 알림이 울린다.
if ! grep -q '^quantlime_container_memory_usage_bytes{' "$METRIC_TMP"; then
    rm -f "$METRIC_TMP"
    echo "[report-container-metrics] '$NAME_PREFIX'로 시작하는 컨테이너가 없어 갱신하지 않습니다." >&2
    exit 1
fi

# 임시 파일에 쓴 뒤 rename - node-exporter가 반쯤 쓰인 파일을 읽지 않게 하기 위함
# (backup-mysql.sh와 동일한 공식 권장 패턴).
mv "$METRIC_TMP" "$METRIC_DIR/container_stats.prom"
