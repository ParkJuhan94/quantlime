#!/usr/bin/env bash
# EC2 호스트에서 cron으로 매일 실행(scripts/install-cron.sh 참고).
# quantlime-prod_upload_data 볼륨(사용자 업로드 이미지)을 S3로 동기화한다.
#
# mysqldump처럼 매번 통째로 새 스냅샷을 뜨는 backup-mysql.sh와 달리
# aws s3 sync를 쓰는 이유 - 업로드 이미지는 한 번 쓰이면 값이 안 바뀌는
# (append-only) 파일들이라, 매일 전체를 다시 압축·업로드하는 것보다
# 변경분만 올리는 sync가 훨씬 저렴하고 빠르다. 이 특성 때문에 --delete
# 없이 순수 추가 동기화만 한다 - 로컬에서 지워진 파일이 있어도 S3 쪽
# 백업은 보존한다(실수로 지운 이미지를 여기서 복구할 수 있게).
#
# amazon/aws-cli 공식 이미지로 볼륨을 마운트해 sync한다 - 호스트에 별도로
# aws cli를 설치하지 않고, 컨테이너가 EC2 인스턴스 프로파일(IAM Role)의
# 자격증명을 IMDS로 그대로 물려받는다(이 프로젝트 인스턴스는
# HttpPutResponseHopLimit=2로 설정돼 있어 컨테이너에서도 IMDSv2 접근 가능
# - 기본값 1이면 브리지 네트워크 한 홉 때문에 컨테이너에서 막힌다).
set -euo pipefail

QUANTLIME_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$QUANTLIME_DIR/.env.prod"
VOLUME_NAME="quantlime-prod_upload_data"

if [ ! -f "$ENV_FILE" ]; then
    echo "[backup-uploads] $ENV_FILE 이 없습니다." >&2
    exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

: "${BACKUP_S3_BUCKET:?BACKUP_S3_BUCKET이 .env.prod에 없습니다}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"

echo "[backup-uploads] 동기화 시작: $VOLUME_NAME -> s3://$BACKUP_S3_BUCKET/uploads/"
docker run --rm \
    -v "$VOLUME_NAME":/data:ro \
    amazon/aws-cli s3 sync /data "s3://$BACKUP_S3_BUCKET/uploads/" --region "$AWS_REGION"

echo "[backup-uploads] 완료"
