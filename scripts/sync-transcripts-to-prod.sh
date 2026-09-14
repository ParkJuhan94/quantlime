#!/usr/bin/env bash
# 로컬 개발 머신에서 실행하는 스크립트(EC2 cron 대상 아님 - scripts/의
# 다른 스크립트와 달리 이것만 로컬 전용).
#
# youtube-transcript-api가 운영 서버(AWS) IP를 차단해(IpBlocked, 2026-09
# 발견) 운영에서 직접 자막을 못 가져오는 문제의 우회로 - 로컬(IP 차단 없음)
# 에서 이미 수집해둔 자막을 운영 DB로 옮긴다. 로컬이 간헐적으로만 켜지는
# 환경이라 "지난번에 뭘 보냈는지" 추적하지 않고, 매번 로컬 DB의 TRANSCRIBED/
# SUMMARIZED 영상 전량을 그대로 보낸다 - 운영 쪽 /api/admin/feed/transcripts/import가
# 이미 처리된 영상은 조용히 스킵하는 멱등 엔드포인트라 안전하다(반복 실행/
# 중간에 끊겨도 다음 실행이 빠진 부분을 마저 채움).
#
# 사용법:
#   PROD_ADMIN_TOKEN="eyJ..." ./scripts/sync-transcripts-to-prod.sh
#
# PROD_ADMIN_TOKEN 얻는 법: quantlime.com에 ROLE_ADMIN 계정(카카오)으로
# 로그인한 뒤, 브라우저 개발자도구 > Application > Local Storage에서
# accessToken 값을 복사한다. 유효시간 30분이라 매번 새로 받아야 한다.
set -euo pipefail

QUANTLIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROD_API_BASE="${PROD_API_BASE:-https://quantlime.com}"
LOCAL_DB_HOST="${LOCAL_DB_HOST:-127.0.0.1}"
LOCAL_DB_PORT="${LOCAL_DB_PORT:-3308}"
LOCAL_DB_USER="${LOCAL_DB_USER:-root}"
LOCAL_DB_PASSWORD="${LOCAL_DB_PASSWORD:-quantlime}"
LOCAL_DB_NAME="${LOCAL_DB_NAME:-quantlime}"

if [ -z "${PROD_ADMIN_TOKEN:-}" ]; then
    echo "[sync-transcripts] PROD_ADMIN_TOKEN 환경변수가 필요합니다(스크립트 상단 주석 참고)." >&2
    exit 1
fi

TSV_FILE="$(mktemp)"
JSON_FILE="$(mktemp)"
RESPONSE_FILE="$(mktemp)"
trap 'rm -f "$TSV_FILE" "$JSON_FILE" "$RESPONSE_FILE"' EXIT

mysql -h "$LOCAL_DB_HOST" -P "$LOCAL_DB_PORT" -u "$LOCAL_DB_USER" -p"$LOCAL_DB_PASSWORD" \
    "$LOCAL_DB_NAME" --default-character-set=utf8mb4 -N -B -e "
    SELECT v.external_video_id, t.source, t.lang, t.content, t.char_count
    FROM video v JOIN transcript t ON t.video_id = v.video_id
    WHERE v.status IN ('TRANSCRIBED','SUMMARIZED');
" > "$TSV_FILE"

ITEM_COUNT=$(wc -l < "$TSV_FILE" | tr -d ' ')
if [ "$ITEM_COUNT" -eq 0 ]; then
    echo "[sync-transcripts] 로컬에 보낼 자막이 없습니다(TRANSCRIBED/SUMMARIZED 영상 0건)."
    exit 0
fi
echo "[sync-transcripts] 로컬에서 $ITEM_COUNT 건의 자막을 찾았습니다. 운영으로 전송합니다..."

python3 -c "
import json, sys
items = []
with open('$TSV_FILE', encoding='utf-8') as f:
    for line in f:
        parts = line.rstrip('\n').split('\t')
        if len(parts) != 5:
            continue
        external_video_id, source, lang, content, char_count = parts
        items.append({
            'externalVideoId': external_video_id,
            'source': source,
            'lang': lang,
            'content': content,
            'charCount': int(char_count),
        })
with open('$JSON_FILE', 'w', encoding='utf-8') as f:
    json.dump({'items': items}, f, ensure_ascii=False)
"

curl -sS -m 120 -X POST \
    -H "Authorization: Bearer $PROD_ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    --data @"$JSON_FILE" \
    "$PROD_API_BASE/api/admin/feed/transcripts/import" > "$RESPONSE_FILE"

# 응답 본문을 셸 문자열로 그대로 파이썬 소스에 끼워넣지 않는다 - 자막
# 내용이 실려있던 요청과 달리 응답 자체는 작지만(outcome 목록뿐), 그래도
# 파일로 넘겨 읽는 쪽이 이스케이프 문제에서 원천적으로 자유롭다.
python3 -c "
import json, collections, sys
with open('$RESPONSE_FILE', encoding='utf-8') as f:
    results = json.load(f)
counts = collections.Counter(r['outcome'] for r in results)
print('[sync-transcripts] 결과:', dict(counts))
for r in results:
    if r['outcome'] == 'IMPORTED':
        print('  IMPORTED:', r['externalVideoId'])
"
