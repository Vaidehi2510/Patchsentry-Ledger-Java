#!/usr/bin/env bash
# End-to-end check against a running Ledger: project -> token -> upload -> query.
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
ADMIN="${LEDGER_ADMIN_TOKEN:?Set LEDGER_ADMIN_TOKEN}"
repo="smoke/app-$RANDOM$RANDOM"

project=$(curl -fsS -X POST "$BASE/api/admin/projects" \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d "{\"repository\":\"$repo\",\"name\":\"Smoke test\"}" | jq -r .id)
token=$(curl -fsS -X POST "$BASE/api/admin/projects/$project/tokens" \
  -H "Authorization: Bearer $ADMIN" | jq -r .token)

record=$(printf '%s' "$repo" | sha256sum | cut -c1-64)
revision=$(printf '%s' "$repo-revision" | sha1sum | cut -c1-40)
body=$(jq -n --arg r "$record" --arg repo "$repo" --arg rev "$revision" '{
  schemaVersion: 1, recordId: $r, repository: $repo,
  pr: {number: 1, title: "Smoke test"}, revision: $rev, phase: "completed",
  checks: [{id: "unit", name: "Unit tests", method: "automated", required: true, status: "passed"}]
}')

code=$(curl -sS -o /tmp/ledger-upload.json -w '%{http_code}' -X PUT "$BASE/api/runner/runs/$record" \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body")
if [ "$code" != "201" ]; then
  echo "Upload failed with HTTP $code"; cat /tmp/ledger-upload.json; exit 1
fi

score=$(curl -fsS "$BASE/api/admin/projects/$project/runs" -H "Authorization: Bearer $ADMIN" | jq -r '.[0].score')
if [ "$score" != "100" ]; then
  echo "Expected score 100, got $score"; exit 1
fi
echo "Smoke test passed: run ${record:0:12} scored $score"
