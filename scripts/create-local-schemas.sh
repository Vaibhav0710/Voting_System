#!/usr/bin/env bash
# Local (non-Docker) database setup on an existing PostgreSQL, with an ordinary login (no superuser):
#   1. creates one schema per service in LOCAL_DB_NAME,
#   2. starts user-, candidate- and voting-service once each (Kafka and Eureka off) so Hibernate creates the
#      tables in their schema exactly as it does in production, then stops them.
# Safe to run again: existing schemas and tables are kept (ddl-auto: update).
#
# Usage:   scripts/create-local-schemas.sh
# Env/.env: VOTING_DB_URL, VOTING_DB_USER, VOTING_DB_PASSWORD, LOCAL_DB_NAME, JWT_SECRET (+ ADMIN_* to seed the admin)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

STARTUP_TIMEOUT_SECONDS=180
# service → schema
declare -A SCHEMAS=([user-service]=userservice [candidate-service]=candidateservice [voting-service]=votingservice)
SERVICE_ORDER=(user-service candidate-service voting-service)
LOG_DIR=$(mktemp -d)

setting() {
  local value="${!1:-$(env_file_value "$1")}"
  [[ -n "$value" ]] || die "set $1 (environment or $ENV_FILE)"
  echo "$value"
}

find_psql() {
  if command -v psql >/dev/null 2>&1; then
    command -v psql
  elif [[ -x "/c/Program Files/PostgreSQL/18/bin/psql.exe" ]]; then
    echo "/c/Program Files/PostgreSQL/18/bin/psql.exe"
  else
    die "psql not found"
  fi
}

load_settings() {
  export VOTING_DB_URL VOTING_DB_USER VOTING_DB_PASSWORD JWT_SECRET JWT_EXPIRATION ADMIN_USERNAME ADMIN_EMAIL ADMIN_PASSWORD
  VOTING_DB_URL=$(setting VOTING_DB_URL)
  VOTING_DB_USER=$(setting VOTING_DB_USER)
  VOTING_DB_PASSWORD=$(setting VOTING_DB_PASSWORD)
  JWT_SECRET=$(setting JWT_SECRET)
  JWT_EXPIRATION=$(setting JWT_EXPIRATION)
  ADMIN_USERNAME="${ADMIN_USERNAME:-$(env_file_value ADMIN_USERNAME)}"
  ADMIN_EMAIL="${ADMIN_EMAIL:-$(env_file_value ADMIN_EMAIL)}"
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(env_file_value ADMIN_PASSWORD)}"
  LOCAL_DB_NAME=$(setting LOCAL_DB_NAME)
}

create_schemas() {
  step "creating schemas in $LOCAL_DB_NAME as $VOTING_DB_USER"
  local hostport=${VOTING_DB_URL#jdbc:postgresql://} schema
  local sql="SET client_min_messages = warning;"
  for schema in "${SCHEMAS[@]}"; do
    sql+="CREATE SCHEMA IF NOT EXISTS $schema;"
  done
  PGPASSWORD="$VOTING_DB_PASSWORD" "$(find_psql)" -h "${hostport%:*}" -p "${hostport##*:}" -U "$VOTING_DB_USER" \
    -d "$LOCAL_DB_NAME" -v ON_ERROR_STOP=1 -q -c "$sql"
  pass "schemas ${SCHEMAS[*]}"
}

# is_ready LOG: true once the service's readiness probe answers 200. Readiness turns UP only after the
# ApplicationReadyEvent listeners (e.g. AdminSeeder) have finished, unlike the "Started …" log line.
is_ready() {
  local port
  port=$(grep -oE "Tomcat started on port [0-9]+" "$1" | grep -oE "[0-9]+$" | head -n 1)
  [[ -n "$port" ]] \
    && [[ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/actuator/health/readiness")" == 200 ]]
}

# create_tables SERVICE: build it, start it against its schema, wait until it is ready, stop it.
create_tables() {
  local service=$1 log="$LOG_DIR/$1.log" pid deadline
  step "$service → schema ${SCHEMAS[$service]}"
  (cd "$ROOT_DIR/$service" && mvn -B -q -DskipTests package) || die "$service did not build"
  # The services' own config names one database each; these overrides point them at one shared database instead.
  java -jar "$ROOT_DIR/$service/target/$service-0.0.1-SNAPSHOT.jar" \
    --spring.datasource.url="$VOTING_DB_URL/$LOCAL_DB_NAME" \
    --spring.datasource.hikari.schema="${SCHEMAS[$service]}" \
    --server.port=0 --eureka.client.enabled=false \
    --spring.kafka.listener.auto-startup=false --spring.kafka.admin.auto-create=false >"$log" 2>&1 &
  pid=$!
  deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
  until is_ready "$log"; do
    if ! kill -0 "$pid" 2>/dev/null || ((SECONDS >= deadline)); then
      kill "$pid" 2>/dev/null || true
      grep -E "ERROR|Caused by" "$log" | head -5 >&2
      die "$service did not become ready (log: $log)"
    fi
    sleep 2
  done
  kill "$pid"
  wait "$pid" 2>/dev/null || true
  pass "$service ready once; Hibernate created its tables and seeders ran"
}

main() {
  load_settings
  create_schemas
  local service
  for service in "${SERVICE_ORDER[@]}"; do
    create_tables "$service"
  done
  rm -rf "$LOG_DIR"
  echo "local schemas ready: $CHECKS_PASSED steps passed"
}

main "$@"
