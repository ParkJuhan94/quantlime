#!/usr/bin/env bash
# 시나리오 파일이 lib/guard.js의 safeGet만 쓰고 있는지 정적으로 검사한다.
# GET 외 메서드나 k6/http 직접 임포트가 하네스에 물리적으로 존재할 수
# 없게 하는 마지막 방어선 - run.sh가 매 실행 전에 이 스크립트를 먼저 돈다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIOS_DIR="$(cd "$SCRIPT_DIR/../scenarios" && pwd)"

FAILED=0

if grep -rnE "http\.(post|put|del|patch|request)\(" "$SCENARIOS_DIR" 2>/dev/null; then
  echo "[preflight] 시나리오에서 GET 외 HTTP 메서드 호출을 발견했다 - 위 목록을 확인할 것." >&2
  FAILED=1
fi

if grep -rn "from 'k6/http'" "$SCENARIOS_DIR" 2>/dev/null; then
  echo "[preflight] 시나리오가 k6/http를 직접 import하고 있다 - lib/guard.js의 safeGet만 쓸 것." >&2
  FAILED=1
fi

if [[ $FAILED -eq 1 ]]; then
  echo "[preflight] 실패 - 위 문제를 고친 뒤 다시 실행할 것." >&2
  exit 1
fi

echo "[preflight] 통과 - 시나리오가 안전 규칙을 지키고 있다."
