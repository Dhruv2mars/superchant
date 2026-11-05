#!/bin/sh
# Reports this workflow's result to the Entire trail as the "CI" check of the dhruv2mars-github-actions plugin
# (an Entire plugin owned by the dhruv2mars project, installed on superchant only, permission checks:write).
# Usage: entire-check.sh in_progress | success | failure | cancelled
# Needs ENTIRE_PLUGIN_KEY (the plugin's Ed25519 private key, PEM) and ENTIRE_PLUGIN_INSTALLATION (ins_...).
set -eu
state=$1
plugin=dhruv2mars-github-actions
core=https://in.auth.entire.io
cell=https://aws-ap-south-1.api.entire.io
repo=repo_01M5Y2BZ4KEG5YXCTAANDDRMNM

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
printf '%s\n' "$ENTIRE_PLUGIN_KEY" > "$tmp/key.pem"

# Plugin JWT (EdDSA, iat 30 s back since the core allows no skew), exchanged for a 45-minute installation token
iat=$(($(date +%s) - 30))
header=$(printf '{"alg":"EdDSA","typ":"JWT"}' | b64url)
claims=$(jq -cn --arg iss "urn:entire:plugin:$plugin" --arg aud "$core/api/authz/sts" --arg plugin "$plugin" \
  --arg ins "$ENTIRE_PLUGIN_INSTALLATION" --argjson iat "$iat" \
  '{iss: $iss, aud: $aud, plugin_id: $plugin, installation_id: $ins, iat: $iat, exp: ($iat + 300)}' | b64url)
printf '%s.%s' "$header" "$claims" > "$tmp/input"
sig=$(openssl pkeyutl -sign -inkey "$tmp/key.pem" -rawin -in "$tmp/input" | b64url)
token=$(curl -fsS -X POST "$core/api/v1/plugins/$plugin/installations/$ENTIRE_PLUGIN_INSTALLATION/access-tokens" \
  -H "Authorization: Bearer $header.$claims.$sig" -H 'Content-Type: application/json' \
  -d '{"permissions":{"checks":"write"}}' | jq -r .token)
echo "::add-mask::$token"

now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
case $state in
  in_progress) status='{"status": "in_progress"}'; title="CI is running" ;;
  success) status="{\"status\": \"completed\", \"conclusion\": \"success\", \"completed_at\": \"$now\"}"; title="CI passed" ;;
  cancelled) status="{\"status\": \"completed\", \"conclusion\": \"cancelled\", \"completed_at\": \"$now\"}"; title="CI was cancelled" ;;
  *) status="{\"status\": \"completed\", \"conclusion\": \"failure\", \"completed_at\": \"$now\"}"; title="CI failed" ;;
esac
# One suite and one run per commit; a re-run (new attempt) updates the same run
jq -n --arg branch "$GITHUB_REF_NAME" --arg id "$GITHUB_RUN_ID" --arg now "$now" --arg url "$url" \
  --arg title "$title" --argjson status "$status" '{
    branch: $branch,
    check_suite: ({key: "github-actions", name: "GitHub Actions", external_id: $id, external_updated_at: $now} + $status),
    check_runs: [{key: "ci", name: "CI", external_id: $id, external_updated_at: $now, details_url: $url,
      output: {title: $title, summary: ($title + ": " + $url)}} + $status]
  }' > "$tmp/body.json"
curl -fsS -X POST "$cell/api/v1/repos/$repo/commits/$GITHUB_SHA/check-runs" \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d @"$tmp/body.json" | jq -c '.results'
