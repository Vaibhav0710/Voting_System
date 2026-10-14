#!/usr/bin/env bash
# Chaos check on the docker compose stack: stop candidate-service while voters are voting.
# Expected:
#   1. votes for an ACTIVE candidate that voting-service has already seen keep succeeding (Redis status-cache fallback),
#   2. a vote for a candidate it has never seen is refused with 503, not accepted blindly,
#   3. the gateway answers 503 JSON on candidate routes,
#   4. after a restart the gateway circuit closes again and voting works normally.
# candidate-service is always started again on exit, even when a check fails.
#
# Usage:   docker compose up -d --build && scripts/chaos.sh
# Env:     GATEWAY_URL, ADMIN_USERNAME / ADMIN_PASSWORD (default: read from .env),
#          VOTERS (default 20), RECOVERY_WAIT_SECONDS (default 180).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

VOTERS="${VOTERS:-20}"
RECOVERY_WAIT_SECONDS="${RECOVERY_WAIT_SECONDS:-180}"
OUTAGE_WAIT_SECONDS=30
# The gateway lets 5 calls through while half-open; 5 successes in a row means the circuit is closed.
CLOSED_STREAK=5
# Register + login is 2 requests per voter; the gateway allows 10/s per IP on auth routes.
REGISTER_PAUSE_SECONDS=0.3
COMPOSE=(docker compose -f "$ROOT_DIR/docker-compose.yml")
CANDIDATE_STOPPED=0
WORK_DIR=$(mktemp -d)

cleanup() {
  if ((CANDIDATE_STOPPED)); then
    echo "restarting candidate-service after a failed run"
    "${COMPOSE[@]}" start candidate-service >/dev/null || true
  fi
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

# new_voter_token PREFIX → registers a fresh voter and prints its JWT
new_voter_token() {
  local username password
  username="${1}_$(date +%s)_${RANDOM}"
  password=$(random_password)
  register_voter "$username" "$password" >/dev/null
  login "$username" "$password"
}

setup() {
  step "admin creates an election; $VOTERS voters register"
  ADMIN_TOKEN=$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD")
  ELECTION_ID=$(new_uuid)
  CANDIDATE_ID=$(create_candidate "$ADMIN_TOKEN" "Chaos Candidate" "$ELECTION_ID")
  VOTER_TOKENS=()
  local i
  for ((i = 0; i < VOTERS; i++)); do
    VOTER_TOKENS+=("$(new_voter_token "chaos$i")")
    sleep "$REGISTER_PAUSE_SECONDS"
  done
  pass "election $ELECTION_ID with $VOTERS voters"

  cast_vote "${VOTER_TOKENS[0]}" "$CANDIDATE_ID" "$ELECTION_ID" "$(new_uuid)"
  expect_status 201 "baseline vote"
  pass "baseline vote accepted while candidate-service is up (status now cached)"
}

stop_candidate_service() {
  step "stopping candidate-service"
  "${COMPOSE[@]}" stop candidate-service >/dev/null
  CANDIDATE_STOPPED=1

  local deadline=$((SECONDS + OUTAGE_WAIT_SECONDS))
  while :; do
    http GET "/candidates/election/$ELECTION_ID"
    [[ "$HTTP_STATUS" == 503 ]] && break
    ((SECONDS < deadline)) || die "gateway did not answer 503 for candidates within ${OUTAGE_WAIT_SECONDS}s"
    sleep 1
  done
  expect_json '.success' false "gateway fallback body"
  pass "gateway answers 503 JSON on candidate routes"
}

# Every remaining voter votes at once; each status lands in its own file.
vote_during_outage() {
  step "$((VOTERS - 1)) voters vote at once while candidate-service is down"
  local i
  for ((i = 1; i < VOTERS; i++)); do
    (
      cast_vote "${VOTER_TOKENS[$i]}" "$CANDIDATE_ID" "$ELECTION_ID" "$(new_uuid)"
      echo "$HTTP_STATUS" >"$WORK_DIR/vote-$i"
    ) &
  done
  wait

  local failed
  failed=$(cat "$WORK_DIR"/vote-* | grep -cv '^201$' || true)
  ((failed == 0)) \
    || die "$failed of $((VOTERS - 1)) votes failed during the outage: $(sort "$WORK_DIR"/vote-* | uniq -c | tr '\n' ' ')"
  pass "all $((VOTERS - 1)) votes for the cached ACTIVE candidate accepted (201)"

  http GET "/votes/election/$ELECTION_ID/count"
  expect_status 200 "vote count"
  expect_json '.data.totalVotes' "$VOTERS" "stored votes"
  pass "$VOTERS votes stored"
}

refuse_unknown_candidate() {
  step "vote for a candidate voting-service has never seen"
  local token
  token=$(new_voter_token "chaos_unknown")
  cast_vote "$token" "$(new_uuid)" "$ELECTION_ID" "$(new_uuid)"
  expect_status 503 "vote for unknown candidate during outage"
  pass "unknown candidate refused with 503 (not accepted blindly)"
}

wait_for_closed_circuit() {
  local deadline=$((SECONDS + RECOVERY_WAIT_SECONDS)) streak=0
  while ((streak < CLOSED_STREAK)); do
    http GET "/candidates/election/$ELECTION_ID"
    if [[ "$HTTP_STATUS" == 200 ]]; then
      streak=$((streak + 1))
      continue
    fi
    streak=0
    ((SECONDS < deadline)) || die "candidate route not healthy within ${RECOVERY_WAIT_SECONDS}s (last $HTTP_STATUS)"
    sleep 2
  done
  pass "gateway circuit closed: $CLOSED_STREAK candidate calls in a row returned 200"
}

start_candidate_service() {
  step "starting candidate-service again"
  "${COMPOSE[@]}" start candidate-service >/dev/null
  CANDIDATE_STOPPED=0
  wait_for_closed_circuit

  local candidate token
  candidate=$(create_candidate "$ADMIN_TOKEN" "Post-recovery Candidate" "$ELECTION_ID")
  token=$(new_voter_token "chaos_after")
  cast_vote "$token" "$candidate" "$ELECTION_ID" "$(new_uuid)"
  expect_status 201 "vote after recovery"
  pass "new candidate created and voted for after recovery"
}

main() {
  require_tools docker
  load_admin_credentials
  step "waiting for $GATEWAY_URL"
  wait_for_gateway
  setup
  stop_candidate_service
  vote_during_outage
  refuse_unknown_candidate
  start_candidate_service
  echo "chaos: all $CHECKS_PASSED checks passed"
}

main "$@"
