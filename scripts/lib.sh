#!/usr/bin/env bash
# Shared helpers for e2e.sh and chaos.sh. Every call goes through the API gateway, as a real client would.
# Source it; do not run it. Needs bash 4+, curl and jq.

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
API="$GATEWAY_URL/api/v1"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
GATEWAY_WAIT_SECONDS="${GATEWAY_WAIT_SECONDS:-120}"
CHECKS_PASSED=0

die() {
  echo "FAIL: $*" >&2
  exit 1
}

step() {
  echo "---- $*"
}

pass() {
  CHECKS_PASSED=$((CHECKS_PASSED + 1))
  echo "  ok  $*"
}

require_tools() {
  local tool
  for tool in curl jq "$@"; do
    command -v "$tool" >/dev/null 2>&1 || die "'$tool' is not installed"
  done
}

# Reads one KEY=value from .env without executing the file.
env_file_value() {
  [[ -f "$ENV_FILE" ]] || return 0
  grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2-
}

# Admin credentials: environment first, then .env (the same values AdminSeeder used).
load_admin_credentials() {
  ADMIN_USERNAME="${ADMIN_USERNAME:-$(env_file_value ADMIN_USERNAME)}"
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(env_file_value ADMIN_PASSWORD)}"
  [[ -n "$ADMIN_USERNAME" && -n "$ADMIN_PASSWORD" ]] \
    || die "set ADMIN_USERNAME and ADMIN_PASSWORD (environment or $ENV_FILE)"
}

new_uuid() {
  local hex
  hex=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
  echo "${hex:0:8}-${hex:8:4}-4${hex:13:3}-a${hex:17:3}-${hex:20:12}"
}

# http METHOD PATH [JSON_BODY] [TOKEN] → sets HTTP_STATUS and HTTP_BODY. PATH is relative to /api/v1.
http() {
  local method=$1 path=$2 body=${3:-} token=${4:-} out
  local args=(-sS -X "$method" "$API$path" -H 'Content-Type: application/json' -w $'\n%{http_code}')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(--data "$body")
  out=$(curl "${args[@]}") || die "$method $path: could not reach $GATEWAY_URL"
  HTTP_STATUS=${out##*$'\n'}
  HTTP_BODY=${out%$'\n'*}
}

# expect_status CODE LABEL: fails with the response's message (never the whole body, it may hold a token).
expect_status() {
  local expected=$1 label=$2 message
  if [[ "$HTTP_STATUS" != "$expected" ]]; then
    message=$(jq -r '.message // empty' <<<"$HTTP_BODY" 2>/dev/null || true)
    die "$label: expected HTTP $expected, got $HTTP_STATUS ${message:+($message)}"
  fi
}

json() {
  jq -r "$1" <<<"$HTTP_BODY"
}

# expect_json FILTER EXPECTED LABEL
expect_json() {
  local actual
  actual=$(json "$1")
  [[ "$actual" == "$2" ]] || die "$3: expected $1 = '$2', got '$actual'"
}

wait_for_gateway() {
  local deadline=$((SECONDS + GATEWAY_WAIT_SECONDS))
  until [[ "$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY_URL/actuator/health")" == "200" ]]; do
    ((SECONDS < deadline)) || die "gateway at $GATEWAY_URL not healthy after ${GATEWAY_WAIT_SECONDS}s"
    sleep 2
  done
}

# login USERNAME PASSWORD → prints the JWT
login() {
  local body
  body=$(jq -n --arg u "$1" --arg p "$2" '{usernameOrEmail: $u, password: $p}')
  http POST /auth/login "$body"
  expect_status 200 "login $1"
  json '.data.token'
}

# register_voter USERNAME PASSWORD → prints the new user id
register_voter() {
  local body
  body=$(jq -n --arg u "$1" --arg p "$2" '{username: $u, email: ($u + "@example.test"), password: $p}')
  http POST /auth/register "$body"
  expect_status 201 "register $1"
  json '.data.id'
}

# create_candidate ADMIN_TOKEN NAME ELECTION_ID → prints the candidate id
create_candidate() {
  local body
  body=$(jq -n --arg n "$2" --arg e "$3" '{name: $n, party: "Test Party", electionId: $e}')
  http POST /candidates "$body" "$1"
  expect_status 201 "create candidate $2"
  json '.data.id'
}

# cast_vote TOKEN CANDIDATE_ID ELECTION_ID IDEMPOTENCY_KEY → sets HTTP_STATUS / HTTP_BODY
cast_vote() {
  local body
  body=$(jq -n --arg c "$2" --arg e "$3" --arg k "$4" '{candidateId: $c, electionId: $e, idempotencyKey: $k}')
  http POST /votes "$body" "$1"
}

random_password() {
  echo "Pw-$(od -An -N9 -tx1 /dev/urandom | tr -d ' \n')"
}
