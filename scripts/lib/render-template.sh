#!/usr/bin/env bash
# Render a template file by replacing __PLACEHOLDER__ tokens.
# Usage: render_template src dest KEY=val KEY2=val2 ...
render_template() {
  local src="$1"
  local dest="$2"
  shift 2

  local content
  content="$(<"$src")"

  local kv key val
  for kv in "$@"; do
    key="${kv%%=*}"
    val="${kv#*=}"
    content="${content//__${key}__/$val}"
  done

  mkdir -p "$(dirname "$dest")"
  printf '%s' "$content" >"$dest"
}

append_unique_line() {
  local file="$1"
  local line="$2"
  if [[ -f "$file" ]] && grep -qF "$line" "$file"; then
    return 0
  fi
  echo "$line" >>"$file"
}

append_json_service() {
  local file="$1"
  local service="$2"
  local path="$3"
  local context="$4"
  local type="$5"

  python3 - "$file" "$service" "$path" "$context" "$type" <<'PY'
import json, sys
path, service, svc_path, ctx, typ = sys.argv[1:6]
with open(path) as f:
    data = json.load(f)
entry = {"service": service, "path": svc_path, "context": ctx, "type": typ}
if any(e.get("service") == service for e in data):
    sys.exit(0)
data.append(entry)
with open(path, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
PY
}
