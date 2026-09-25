#!/usr/bin/env bash
set -euo pipefail

# This is the forced SSH command for the GitHub Actions deploy key. It accepts
# application archives on stdin and one fixed log-view command; it never opens a shell.
umask 077

# The same restricted key may request a bounded journal excerpt for debugging.
# No arbitrary shell command is ever evaluated.
case "${SSH_ORIGINAL_COMMAND:-}" in
  logs)
    exec journalctl --user --unit=cabinet.service --since "24 hours ago" \
      --grep='ERROR|Exception|Caused by|SQLSTATE|SQLState' --case-sensitive=no \
      --no-pager --output=short-iso -n 150
    ;;
  "")
    ;;
  *)
    echo "Rejected SSH command" >&2
    exit 64
    ;;
esac

root="$HOME/cabinet"
self_script="$(readlink -f -- "${BASH_SOURCE[0]}")"
incoming="$(mktemp -d "$root/incoming.XXXXXX")"
release_id="$(date -u +%Y%m%d%H%M%S)-$$"
release="$root/releases/$release_id"
archive="$incoming/deploy.tar.gz"
app="$root/app"
previous_jar="$incoming/app.jar.previous"
previous_runtime="$incoming/runtime-extra.env.previous"
had_jar=false
had_runtime=false

cleanup() {
  rm -rf "$incoming"
  find "$root/releases" -mindepth 1 -maxdepth 1 -type d -mtime +7 -exec rm -rf -- {} +
}
trap cleanup EXIT

cat > "$archive"
tar -tzf "$archive" | awk '
  /^\// { bad = 1 }
  /(^|\/)\.\.(\/|$)/ { bad = 1 }
  END { exit bad }
' || { echo "Rejected unsafe deployment archive" >&2; exit 1; }

mkdir -m 0700 "$release"
tar --no-same-owner --no-same-permissions -xzf "$archive" -C "$release"
test -s "$release/app.jar" || { echo "Deployment archive is missing app.jar" >&2; exit 1; }

if [[ -f "$app/app.jar" ]]; then
  cp -p "$app/app.jar" "$previous_jar"
  had_jar=true
fi
if [[ -f "$app/runtime-extra.env" ]]; then
  cp -p "$app/runtime-extra.env" "$previous_runtime"
  had_runtime=true
fi

if [[ -f "$release/.cabinet-runtime.env" ]]; then
  install -m 0600 "$release/.cabinet-runtime.env" "$app/runtime-extra.env.new"
  mv -f "$app/runtime-extra.env.new" "$app/runtime-extra.env"
fi
install -m 0640 "$release/app.jar" "$app/app.jar.new"
mv -f "$app/app.jar.new" "$app/app.jar"

systemctl --user daemon-reload
systemctl --user restart cabinet.service

ready=false
for attempt in $(seq 1 90); do
  if curl --fail --silent http://127.0.0.1:8080/v1/auth/csrf >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != true ]]; then
  echo "Recent Cabinet service logs:" >&2
  journalctl --user --unit=cabinet.service --no-pager --output=short-iso -n 250 >&2 || true
  if [[ "$had_jar" == true ]]; then
    cp -p "$previous_jar" "$app/app.jar"
  else
    rm -f "$app/app.jar"
  fi
  if [[ "$had_runtime" == true ]]; then
    cp -p "$previous_runtime" "$app/runtime-extra.env"
  else
    rm -f "$app/runtime-extra.env"
  fi
  systemctl --user restart cabinet.service || true
  echo "Cabinet failed its readiness check; the prior release was restored when available" >&2
  exit 1
fi

if [[ -f "$release/ops/cabinet-receive-deploy.sh" ]]; then
  install -m 0700 "$release/ops/cabinet-receive-deploy.sh" "$self_script.new"
  mv -f "$self_script.new" "$self_script"
fi

echo "Cabinet deployed successfully: $release_id"
