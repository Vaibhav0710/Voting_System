#!/usr/bin/env bash
# End-to-end check of a running stack through the gateway:
# register → login → create candidates → vote → replay → duplicate → receipt → verify → chain → results → audit.
# Exits non-zero at the first mismatch.
#
# Usage:   docker compose up -d --build && scripts/e2e.sh
# Env:     GATEWAY_URL (default http://localhost:8080), ADMIN_USERNAME / ADMIN_PASSWORD (default: read from .env),
#          RESULTS_WAIT_SECONDS (default 30; results arrive through Kafka).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

RESULTS_WAIT_SECONDS="${RESULTS_WAIT_SECONDS:-30}"

setup_election() {
  step "admin creates an election with two candidates"
  ADMIN_TOKEN=$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD")
  ELECTION_ID=$(new_uuid)
  CANDIDATE_A=$(create_candidate "$ADMIN_TOKEN" "E2E Candidate A" "$ELECTION_ID")
  CANDIDATE_B=$(create_candidate "$ADMIN_TOKEN" "E2E Candidate B" "$ELECTION_ID")
  pass "election $ELECTION_ID, candidates created"

  http GET "/candidates/election/$ELECTION_ID/active"
  expect_status 200 "list active candidates (anonymous)"
  expect_json '.data | length' 2 "active candidates"
  pass "anonymous user sees both ACTIVE candidates"
}

setup_voter() {
  step "a voter registers and logs in"
  local username password
  username="e2e_$(date +%s)_$RANDOM"
  password=$(random_password)
  VOTER_ID=$(register_voter "$username" "$password")
  VOTER_TOKEN=$(login "$username" "$password")
  pass "voter $username registered"

  http POST /candidates "{\"name\":\"x\",\"electionId\":\"$ELECTION_ID\"}" "$VOTER_TOKEN"
  expect_status 403 "voter creates a candidate"
  pass "voter cannot create candidates (403)"
}

check_vote_rules() {
  step "voting"
  cast_vote "" "$CANDIDATE_A" "$ELECTION_ID" "$(new_uuid)"
  expect_status 401 "anonymous vote"
  pass "anonymous vote rejected (401)"

  local key
  key=$(new_uuid)
  cast_vote "$VOTER_TOKEN" "$CANDIDATE_A" "$ELECTION_ID" "$key"
  expect_status 201 "cast vote"
  VOTE_ID=$(json '.data.voteId')
  VOTE_HASH=$(json '.data.voteHash')
  pass "vote cast (201), id $VOTE_ID"

  cast_vote "$VOTER_TOKEN" "$CANDIDATE_A" "$ELECTION_ID" "$key"
  expect_status 200 "replay with the same idempotency key"
  expect_json '.data.voteId' "$VOTE_ID" "replay receipt"
  expect_json '.data.replay' true "replay flag"
  pass "same idempotency key returns the original receipt (200)"

  cast_vote "$VOTER_TOKEN" "$CANDIDATE_B" "$ELECTION_ID" "$(new_uuid)"
  expect_status 409 "second vote in the same election"
  pass "second vote rejected (409)"
}

check_receipt_and_integrity() {
  step "receipt, verification and chain"
  http GET "/votes/$VOTE_ID" "" "$VOTER_TOKEN"
  expect_status 200 "vote receipt"
  expect_json '.data.voteHash' "$VOTE_HASH" "receipt hash"
  pass "receipt matches the cast vote"

  http POST "/votes/verify/$VOTE_ID" "" "$VOTER_TOKEN"
  expect_status 200 "verify vote"
  expect_json '.data.hashValid' true "vote hash"
  pass "vote hash verified"

  http POST "/votes/chain/validate/$ELECTION_ID" "" "$ADMIN_TOKEN"
  expect_status 200 "validate chain"
  expect_json '.data.chainValid' true "chain"
  expect_json '.data.totalVotesChecked' 1 "votes in chain"
  pass "election chain valid"
}

check_results_and_audit() {
  step "results (via Kafka) and audit"
  local deadline=$((SECONDS + RESULTS_WAIT_SECONDS))
  while :; do
    http GET "/results/election/$ELECTION_ID"
    [[ "$HTTP_STATUS" == 200 && "$(json '.data.totalVotes')" == 1 ]] && break
    ((SECONDS < deadline)) || die "results: totalVotes did not reach 1 within ${RESULTS_WAIT_SECONDS}s"
    sleep 1
  done
  expect_json ".data.candidateResults[] | select(.candidateId == \"$CANDIDATE_A\") | .voteCount" 1 "votes for A"
  pass "results count the vote for candidate A"

  http GET "/results/election/$ELECTION_ID/audit" "" "$ADMIN_TOKEN"
  expect_status 200 "audit trail"
  expect_json '.data.totalVotes' 1 "audit total"
  expect_json '.data.auditEntries[0].voteId' "$VOTE_ID" "audit vote id"
  expect_json '.data.auditEntries[0].voteHash' "$VOTE_HASH" "audit vote hash"
  pass "audit trail lists the vote with its hash"

  http GET "/results/election/$ELECTION_ID/audit" "" "$VOTER_TOKEN"
  expect_status 403 "voter reads audit trail"
  pass "voter cannot read the audit trail (403)"
}

main() {
  require_tools
  load_admin_credentials
  step "waiting for $GATEWAY_URL"
  wait_for_gateway
  setup_election
  setup_voter
  check_vote_rules
  check_receipt_and_integrity
  check_results_and_audit
  echo "e2e: all $CHECKS_PASSED checks passed"
}

main "$@"
