#!/usr/bin/env bash

set -euo pipefail

load_test_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${load_test_dir}/.." && pwd)"
env_file="${1:-${repo_root}/.env.local}"
fixture_dir="${load_test_dir}/fixtures"

if [[ ! -f "${env_file}" ]]; then
  echo "환경 파일을 찾을 수 없습니다: ${env_file}" >&2
  exit 1
fi

set -a
source "${env_file}"
set +a

: "${DB_URL:?DB_URL이 필요합니다.}"
: "${DB_USERNAME:?DB_USERNAME이 필요합니다.}"
: "${DB_PASSWORD:?DB_PASSWORD가 필요합니다.}"
: "${CONTENT_STORAGE_TYPE:?CONTENT_STORAGE_TYPE이 필요합니다.}"
: "${CONTENT_LOCAL_ROOT:?CONTENT_LOCAL_ROOT가 필요합니다.}"

if [[ "${CONTENT_STORAGE_TYPE}" != "local" ]]; then
  echo "로컬 시드는 CONTENT_STORAGE_TYPE=local에서만 실행할 수 있습니다." >&2
  exit 1
fi

case "${DB_URL}" in
  jdbc:log4jdbc:mysql://*) db_target="${DB_URL#jdbc:log4jdbc:mysql://}" ;;
  jdbc:mysql://*) db_target="${DB_URL#jdbc:mysql://}" ;;
  *)
    echo "지원하지 않는 DB_URL 형식입니다. 로컬 MySQL JDBC URL을 사용해주세요." >&2
    exit 1
    ;;
esac

db_target="${db_target%%\?*}"
db_authority="${db_target%%/*}"
db_name="${db_target#*/}"

if [[ "${db_authority}" == *:* ]]; then
  db_host="${db_authority%%:*}"
  db_port="${db_authority##*:}"
else
  db_host="${db_authority}"
  db_port="3306"
fi

case "${db_host}" in
  localhost|127.0.0.1) ;;
  *)
    echo "안전상 로컬 MySQL(localhost 또는 127.0.0.1)에서만 실행할 수 있습니다." >&2
    exit 1
    ;;
esac

if [[ -z "${db_name}" || "${db_name}" == "${db_target}" ]]; then
  echo "DB_URL에서 데이터베이스 이름을 확인할 수 없습니다." >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql 명령을 찾을 수 없습니다. MySQL 클라이언트를 먼저 설치해주세요." >&2
  exit 1
fi

mysql_args=(
  --protocol=TCP
  --default-character-set=utf8mb4
  -h "${db_host}"
  -P "${db_port}"
  -u "${DB_USERNAME}"
  "${db_name}"
  --batch
  --raw
  --skip-column-names
)

mysql_value() {
  MYSQL_PWD="${DB_PASSWORD}" mysql "${mysql_args[@]}" -e "$1"
}

active_user_count="$(mysql_value "SELECT COUNT(*) FROM users WHERE status = 'ACTIVE';")"
if [[ "${active_user_count}" -lt 1 ]]; then
  echo "작성자로 사용할 활성 사용자가 없습니다. 테스트 계정으로 회원가입을 먼저 실행해주세요." >&2
  exit 1
fi

non_fixture_chapter_count="$(mysql_value "SELECT COUNT(*) FROM main_chapters WHERE main_chapter_id NOT BETWEEN 9100001 AND 9100005;")"
if [[ "${non_fixture_chapter_count}" -gt 0 ]]; then
  echo "이미 다른 대단원 데이터가 존재하여 로컬 테스트 시드를 중단합니다." >&2
  echo "비어 있는 로컬 테스트 DB 또는 기존 시드 DB에서 실행해주세요." >&2
  exit 1
fi

if [[ "${CONTENT_LOCAL_ROOT}" = /* ]]; then
  content_root="${CONTENT_LOCAL_ROOT}"
else
  content_root="${repo_root}/${CONTENT_LOCAL_ROOT#./}"
fi

if [[ -z "${content_root}" || "${content_root}" == "/" ]]; then
  echo "안전하지 않은 CONTENT_LOCAL_ROOT입니다." >&2
  exit 1
fi

content_version_dir="${content_root}/learning/sub-chapters/9200001/lesson.json/.versions/load-test-v1"
mkdir -p "${content_version_dir}"
cp "${fixture_dir}/foundation-lesson.json" "${content_version_dir}/content"
printf '%s' 'application/json' > "${content_version_dir}/content-type"

MYSQL_PWD="${DB_PASSWORD}" mysql "${mysql_args[@]}" < "${fixture_dir}/local-seed.sql"

seed_summary="$(mysql_value "SELECT CONCAT('대단원 ', COUNT(*), '개') FROM main_chapters WHERE main_chapter_id BETWEEN 9100001 AND 9100005; SELECT CONCAT('문항 ', COUNT(*), '개') FROM quiz_questions WHERE question_id BETWEEN 9300001 AND 9300012; SELECT CONCAT('공개 강좌 ', COUNT(*), '개') FROM content_versions WHERE content_version_id = 9400001 AND status = 'PUBLISHED'; SELECT CONCAT('퀴즈 보상 정책 ', COUNT(*), '개') FROM system_policies WHERE policy_id = 9500001 AND is_active = TRUE; SELECT CONCAT('금융상품 ', COUNT(*), '개') FROM financial_products WHERE product_id BETWEEN 9600001 AND 9600008 AND is_active = TRUE; SELECT CONCAT('상품 기준 가격 ', COUNT(*), '개') FROM product_prices WHERE product_price_id BETWEEN 9700001 AND 9700004;")"

echo "로컬 테스트 시드 생성이 완료되었습니다."
echo "${seed_summary}"
echo "콘텐츠 저장 위치: ${content_root}"
