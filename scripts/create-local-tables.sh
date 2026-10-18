#!/usr/bin/env bash
# Local (non-Docker) table setup on an existing PostgreSQL. Needs the voting_system database with the schemas
# user_service, candidate_service and voting_service already created (docs/DATABASE.md, "Local run").
# Starts user-, candidate- and voting-service once each (Kafka and Eureka off) so Hibernate creates the tables in
# their schema exactly as it does in production and the seeders run, then stops them.
# Safe to run again: existing tables and rows are kept (ddl-auto: update).
#
# Usage:   scripts/create-local-tables.sh
# Env/.env: VOTING_DB_URL, VOTING_DB_USER, VOTING_DB_PASSWORD, JWT_SECRET (+ ADMIN_* to seed the admin)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

STARTUP_TIMEOUT_SECONDS=180
SERVICE_ORDER=(user-service candidate-service voting-service)
LOG_DIR=$(mktemp -d)

setting() {
  local value="${!1:-$(env_file_value "$1")}"
  [[ -n "$value" ]] || die "set $1 (environment or $ENV_FILE)"
  echo "$value"
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
}

# is_ready LOG: true once the service's readiness probe answers 200. Readiness turns UP only after the
# ApplicationReadyEvent listeners (e.g. AdminSeeder) have finished, unlike the "Started …" log line.
is_ready() {
  local port
  port=$(grep -oE "Tomcat started on port [0-9]+" "$1" | grep -oE "[0-9]+$" | head -n 1)
  [[ -n "$port" ]] \
    && [[ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/actuator/health/readiness")" == 200 ]]
}

# create_tables SERVICE: build it, start it with its normal config, wait until it is ready, stop it.
create_tables() {
  local service=$1 log="$LOG_DIR/$1.log" pid deadline
  step "$service"
  (cd "$ROOT_DIR/$service" && mvn -B -q -DskipTests package) || die "$service did not build"
  java -jar "$ROOT_DIR/$service/target/$service-0.0.1-SNAPSHOT.jar" \
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
  local service
  for service in "${SERVICE_ORDER[@]}"; do
    create_tables "$service"
  done
  rm -rf "$LOG_DIR"
  echo "local tables ready: $CHECKS_PASSED steps passed"
}

main "$@"
