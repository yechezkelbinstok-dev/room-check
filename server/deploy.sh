#!/bin/sh
# Puts worker.single.js live on Cloudflare as room-check-sync, keeping its KV store and its
# SYNC_TOKEN exactly as they are. The key is read from the environment, never from a file here:
#   CF_EMAIL    the Cloudflare login email
#   CF_KEY      a Cloudflare API key (the global key, sent with the email)
#   CF_ACCOUNT  the account id
set -e
: "${CF_EMAIL:?set CF_EMAIL}" "${CF_KEY:?set CF_KEY}" "${CF_ACCOUNT:?set CF_ACCOUNT}"
cd "$(dirname "$0")"
node build-single.mjs >/dev/null
node worker.single.test.mjs
curl -sS -X PUT "https://api.cloudflare.com/client/v4/accounts/$CF_ACCOUNT/workers/scripts/room-check-sync" \
  -H "X-Auth-Email: $CF_EMAIL" -H "X-Auth-Key: $CF_KEY" \
  -F 'metadata={"main_module":"worker.js","compatibility_date":"2026-09-04","keep_bindings":["kv_namespace","secret_text"]};type=application/json' \
  -F "worker.js=@worker.single.js;filename=worker.js;type=application/javascript+module" \
| node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const r=JSON.parse(s);
  if(!r.success){console.error("deploy failed:",JSON.stringify(r.errors));process.exit(1);}
  console.log("deployed room-check-sync,", r.result.modified_on);})'
