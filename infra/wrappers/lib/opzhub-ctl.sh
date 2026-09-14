# Process-lifecycle helpers for opzhubctl.
# PIDs and service logs live under /var/lib and /var/log (kept across upgrades).
# SPDX-License-Identifier: Apache-2.0
# shellcheck shell=bash

# Operator ids: opzgui (SPA), opzbe (Java), opzpy (Python core), opzgw (gateway).

opzhub_ctl_resolve() {
  local raw="${1:-}"
  raw="$(printf '%s' "${raw}" | tr '[:upper:]' '[:lower:]')"
  case "${raw}" in
    opzgui|gui) printf '%s\n' opzgui ;;
    opzbe|be) printf '%s\n' opzbe ;;
    opzpy|py) printf '%s\n' opzpy ;;
    opzgw|gw) printf '%s\n' opzgw ;;
    all) printf '%s\n' all ;;
    *) return 1 ;;
  esac
}

opzhub_ctl_role() {
  case "$1" in
    opzgui) printf '%s\n' "frontend SPA (:8109)" ;;
    opzbe) printf '%s\n' "Java backend (:8114)" ;;
    opzpy) printf '%s\n' "Python API and workers (:8117)" ;;
    opzgw) printf '%s\n' "public gateway (nginx)" ;;
    *) printf '%s\n' "$1" ;;
  esac
}

opzhub_ctl_wrapper() {
  local wrap="${OPZHUB_WRAP:-${OPZHUB_HOME}/infra/wrappers}"
  case "$1" in
    opzbe) printf '%s\n' "${wrap}/run-opzhub-engine.sh" ;;
    opzgui) printf '%s\n' "${wrap}/run-web.sh" ;;
    opzpy) printf '%s\n' "${wrap}/run-opzhub-be-core.sh" ;;
    opzgw) printf '%s\n' "${wrap}/run-nginx.sh" ;;
    *) return 1 ;;
  esac
}

opzhub_ctl_compose_name() {
  case "$1" in
    opzbe) printf '%s\n' opzhub-be-app ;;
    opzgui) printf '%s\n' opzhub-web-app ;;
    opzpy) printf '%s\n' opzhub-be-core ;;
    opzgw) printf '%s\n' opzhub-ui-service ;;
    *) return 1 ;;
  esac
}

opzhub_ctl_is_optional() {
  case "$1" in
    opzpy) return 0 ;;
    *) return 1 ;;
  esac
}

opzhub_ctl_bio_wanted() {
  [ "${OPZHUB_ENABLE_BIO:-0}" = "1" ] && return 0
  local f methods
  for f in \
    "${PLATFORM_CONFIG_PATH:-}" \
    "${OPZHUB_CONFIG}/platform.override.yaml" \
    "${OPZHUB_CONFIG}/platform.yaml" \
    "${OPZHUB_HOME}/platform/config/platform.yaml"
  do
    [ -n "${f}" ] && [ -f "${f}" ] || continue
    methods="$(grep -E '^[[:space:]]*methods:' "${f}" || true)"
    echo "${methods}" | grep -Eq '\bface\b' && return 0
    if echo "${methods}" | grep -Eq '\bfingerprint\b'; then
      grep -Eq '^[[:space:]]+mode:.*template' "${f}" && return 0
    fi
  done
  return 1
}

opzhub_ctl_python_workers_wanted() {
  [ "${OPZHUB_ENABLE_AI:-0}" = "1" ] && return 0
  [ -d "${OPZHUB_HOME}/modules/ocr" ] && return 0
  [ -d "${OPZHUB_HOME}/modules/image-processing" ] && return 0
  [ -d "${OPZHUB_HOME}/modules/mail" ] && return 0
  return 1
}

opzhub_ctl_python_api_wanted() {
  opzhub_ctl_python_workers_wanted && return 0
  opzhub_ctl_bio_wanted && return 0
  return 1
}

opzhub_ctl_python_wanted() {
  opzhub_ctl_python_api_wanted
}

opzhub_ctl_present() {
  case "$1" in
    opzpy)
      opzhub_ctl_python_wanted
      return $?
      ;;
  esac
  return 0
}

opzhub_ctl_ids_all() {
  printf '%s\n' opzgw opzgui opzbe
  if opzhub_ctl_python_wanted; then
    printf '%s\n' opzpy
  fi
}

opzhub_ctl_pidfile() { printf '%s\n' "${OPZHUB_RUN}/svc/${1}.pid"; }
opzhub_ctl_logfile() { printf '%s\n' "${OPZHUB_LOGS}/svc/${1}.log"; }

opzhub_ctl_compose_file() {
  local f
  for f in \
    "${OPZHUB_COMPOSE_FILE:-}" \
    "${OPZHUB_CONFIG}/docker-compose.yml" \
    "${OPZHUB_HOME}/infra/docker-compose.yml" \
    "${OPZHUB_HOME}/docker-compose.yml"
  do
    if [ -n "${f}" ] && [ -f "${f}" ]; then
      printf '%s\n' "${f}"
      return 0
    fi
  done
  return 1
}

opzhub_ctl_runtime() {
  case "${OPZHUB_RUNTIME:-auto}" in
    compose|host)
      printf '%s\n' "${OPZHUB_RUNTIME}"
      ;;
    auto)
      if opzhub_ctl_compose_file >/dev/null && command -v docker >/dev/null 2>&1; then
        printf '%s\n' compose
      else
        printf '%s\n' host
      fi
      ;;
    *)
      opzhub_die "OPZHUB_RUNTIME must be auto, compose, or host"
      ;;
  esac
}

opzhub_ctl_compose() {
  local file
  file="$(opzhub_ctl_compose_file)" || opzhub_die "no compose file (set OPZHUB_COMPOSE_FILE)"
  docker compose \
    --project-name "${OPZHUB_COMPOSE_PROJECT:-opzhub}" \
    --project-directory "${OPZHUB_HOME}" \
    -f "${file}" \
    "$@"
}

opzhub_ctl_compose_profiles() {
  local args=()
  if opzhub_ctl_python_api_wanted; then
    args+=(--profile ai)
  fi
  if [ -d "${OPZHUB_HOME}/modules/ocr" ] || [ "${OPZHUB_ENABLE_AI:-0}" = "1" ]; then
    args+=(--profile ocr)
  fi
  if [ -d "${OPZHUB_HOME}/modules/image-processing" ] || [ "${OPZHUB_ENABLE_AI:-0}" = "1" ]; then
    args+=(--profile image)
  fi
  # Kafka is optional later and is not implemented: never --profile kafka ([11]).
  # OPZHUB_ENABLE_KAFKA is ignored in this drop.
  if [ "${#args[@]}" -gt 0 ]; then
    printf '%s\n' "${args[@]}"
  fi
}

opzhub_ctl_host_alive() {
  local pidfile pid
  pidfile="$(opzhub_ctl_pidfile "$1")"
  [ -f "${pidfile}" ] || return 1
  pid="$(tr -d '[:space:]' <"${pidfile}" 2>/dev/null || true)"
  [ -n "${pid}" ] || return 1
  kill -0 "${pid}" 2>/dev/null
}

opzhub_ctl_host_reap() {
  local pidfile
  pidfile="$(opzhub_ctl_pidfile "$1")"
  if [ -f "${pidfile}" ] && ! opzhub_ctl_host_alive "$1"; then
    rm -f "${pidfile}"
  fi
}

opzhub_ctl_start_host() {
  local id="$1" wrapper pidfile logfile pid
  opzhub_ctl_host_reap "${id}"
  if opzhub_ctl_host_alive "${id}"; then
    printf '%s already running (pid %s)\n' "${id}" "$(tr -d '[:space:]' <"$(opzhub_ctl_pidfile "${id}")")"
    return 0
  fi
  wrapper="$(opzhub_ctl_wrapper "${id}")"
  [ -x "${wrapper}" ] || [ -f "${wrapper}" ] || opzhub_die "missing wrapper: ${wrapper}"
  pidfile="$(opzhub_ctl_pidfile "${id}")"
  logfile="$(opzhub_ctl_logfile "${id}")"
  mkdir -p "$(dirname "${pidfile}")" "$(dirname "${logfile}")"
  # New session: stop can signal the whole group. exec in the wrapper keeps this pid.
  nohup setsid bash "${wrapper}" >>"${logfile}" 2>&1 </dev/null &
  pid=$!
  printf '%s\n' "${pid}" >"${pidfile}"
  sleep 0.4
  if ! kill -0 "${pid}" 2>/dev/null; then
    rm -f "${pidfile}"
    printf '%s failed to stay up; see %s\n' "${id}" "${logfile}" >&2
    return 1
  fi
  printf '%s started (pid %s) log %s\n' "${id}" "${pid}" "${logfile}"
}

opzhub_ctl_stop_host() {
  local id="$1" pidfile pid i
  pidfile="$(opzhub_ctl_pidfile "${id}")"
  opzhub_ctl_host_reap "${id}"
  if ! opzhub_ctl_host_alive "${id}"; then
    printf '%s already stopped\n' "${id}"
    rm -f "${pidfile}"
    return 0
  fi
  pid="$(tr -d '[:space:]' <"${pidfile}")"
  kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "${pid}" 2>/dev/null || true
  i=0
  while [ "${i}" -lt 15 ] && kill -0 "${pid}" 2>/dev/null; do
    sleep 1
    i=$((i + 1))
  done
  if kill -0 "${pid}" 2>/dev/null; then
    kill -KILL -- "-${pid}" 2>/dev/null || kill -KILL "${pid}" 2>/dev/null || true
    sleep 0.2
  fi
  rm -f "${pidfile}"
  printf '%s stopped\n' "${id}"
}

opzhub_ctl_start_compose() {
  local id="$1" name
  name="$(opzhub_ctl_compose_name "${id}")"
  # shellcheck disable=SC2046
  opzhub_ctl_compose $(opzhub_ctl_compose_profiles) up -d "${name}"
}

opzhub_ctl_stop_compose() {
  local id="$1" name
  name="$(opzhub_ctl_compose_name "${id}")"
  opzhub_ctl_compose stop "${name}"
}

opzhub_ctl_start_stack() {
  local id rt
  rt="$(opzhub_ctl_runtime)"
  if [ "${rt}" = compose ]; then
    # One up -d so depends_on/health order is respected.
    # shellcheck disable=SC2046
    opzhub_ctl_compose $(opzhub_ctl_compose_profiles) up -d
    return
  fi
  while IFS= read -r id; do
    [ -n "${id}" ] || continue
    opzhub_ctl_start_one "${id}" || return 1
  done <<EOF
$(opzhub_ctl_ids_all)
EOF
}

opzhub_ctl_stop_stack() {
  local id rt
  rt="$(opzhub_ctl_runtime)"
  if [ "${rt}" = compose ]; then
    opzhub_ctl_compose stop
    return
  fi
  # Reverse of start: gateway first, then apps.
  for id in opzgw opzpy opzbe opzgui; do
    opzhub_ctl_present "${id}" || continue
    opzhub_ctl_stop_one "${id}" || true
  done
}

opzhub_ctl_restart_stack() {
  local rt
  rt="$(opzhub_ctl_runtime)"
  if [ "${rt}" = compose ]; then
    opzhub_ctl_compose restart
    return
  fi
  opzhub_ctl_stop_stack
  opzhub_ctl_start_stack
}

opzhub_ctl_start_one() {
  local id="$1" rt
  rt="$(opzhub_ctl_runtime)"
  case "${rt}" in
    compose) opzhub_ctl_start_compose "${id}" ;;
    host) opzhub_ctl_start_host "${id}" ;;
  esac
}

opzhub_ctl_stop_one() {
  local id="$1" rt
  rt="$(opzhub_ctl_runtime)"
  case "${rt}" in
    compose) opzhub_ctl_stop_compose "${id}" ;;
    host) opzhub_ctl_stop_host "${id}" ;;
  esac
}

opzhub_ctl_restart_one() {
  local id="$1" rt name
  rt="$(opzhub_ctl_runtime)"
  if [ "${rt}" = compose ]; then
    name="$(opzhub_ctl_compose_name "${id}")"
    opzhub_ctl_compose restart "${name}"
    return
  fi
  opzhub_ctl_stop_host "${id}"
  opzhub_ctl_start_host "${id}"
}

opzhub_ctl_expand_targets() {
  local t id
  if [ $# -eq 0 ] || { [ $# -eq 1 ] && [ "$1" = all ]; }; then
    opzhub_ctl_ids_all
    return 0
  fi
  for t in "$@"; do
    id="$(opzhub_ctl_resolve "${t}")" || {
      printf 'unknown service: %s (try: opzhubctl list)\n' "${t}" >&2
      return 5
    }
    if [ "${id}" = all ]; then
      opzhub_ctl_ids_all
    else
      printf '%s\n' "${id}"
    fi
  done | awk 'NF && !seen[$0]++'
}

opzhub_ctl_status_one() {
  local id="$1" rt state extra
  rt="$(opzhub_ctl_runtime)"
  extra="$(opzhub_ctl_role "${id}")"
  if [ "${rt}" = compose ]; then
    local name
    name="$(opzhub_ctl_compose_name "${id}")"
    if opzhub_ctl_compose ps --status running --services 2>/dev/null | grep -qx "${name}"; then
      state="running"
    else
      state="stopped"
    fi
    printf '%-8s %-10s %s\n' "${id}" "${state}" "${extra}"
    [ "${state}" = running ]
    return
  fi
  opzhub_ctl_host_reap "${id}"
  if opzhub_ctl_host_alive "${id}"; then
    state="running"
    extra="${extra}  pid=$(tr -d '[:space:]' <"$(opzhub_ctl_pidfile "${id}")")"
  else
    state="stopped"
  fi
  printf '%-8s %-10s %s\n' "${id}" "${state}" "${extra}"
  [ "${state}" = running ]
}

opzhub_ctl_logs() {
  local id follow=0 lines=100 rt name logfile
  while [ $# -gt 0 ]; do
    case "$1" in
      -f|--follow) follow=1; shift ;;
      -n|--tail) lines="${2:?}"; shift 2 ;;
      -h|--help)
        printf '%s\n' "usage: opzhubctl logs SERVICE [-f] [-n LINES]"
        return 0
        ;;
      *)
        id="$(opzhub_ctl_resolve "$1")" || {
          printf 'unknown service: %s\n' "$1" >&2
          return 5
        }
        [ "${id}" != all ] || {
          printf 'logs requires one service, not all\n' >&2
          return 5
        }
        shift
        ;;
    esac
  done
  [ -n "${id:-}" ] || {
    printf '%s\n' "usage: opzhubctl logs SERVICE [-f] [-n LINES]" >&2
    return 5
  }
  rt="$(opzhub_ctl_runtime)"
  if [ "${rt}" = compose ]; then
    name="$(opzhub_ctl_compose_name "${id}")"
    if [ "${follow}" -eq 1 ]; then
      opzhub_ctl_compose logs --tail "${lines}" -f "${name}"
    else
      opzhub_ctl_compose logs --tail "${lines}" "${name}"
    fi
    return
  fi
  logfile="$(opzhub_ctl_logfile "${id}")"
  [ -f "${logfile}" ] || opzhub_die "no log yet: ${logfile}"
  if [ "${follow}" -eq 1 ]; then
    tail -n "${lines}" -f "${logfile}"
  else
    tail -n "${lines}" "${logfile}"
  fi
}

opzhub_ctl_kv() {
  local file="$1" key="$2" line
  [ -f "${file}" ] || return 0
  while IFS= read -r line || [ -n "${line}" ]; do
    case "${line}" in
      \#*|'') continue ;;
    esac
    if [ "${line%%=*}" = "${key}" ]; then
      printf '%s\n' "${line#*=}"
      return 0
    fi
  done <"${file}"
}

opzhub_ctl_release() {
  local rel="${OPZHUB_HOME}/RELEASE"
  [ -f "${rel}" ] || rel="${OPZHUB_HOME}/platform/RELEASE"
  local version build built product short
  product="$(opzhub_ctl_kv "${rel}" PRODUCT)"
  short="$(opzhub_ctl_kv "${rel}" SHORT)"
  version="$(opzhub_ctl_kv "${rel}" VERSION)"
  build="$(opzhub_ctl_kv "${rel}" BUILD)"
  built="$(opzhub_ctl_kv "${rel}" BUILT_AT)"
  : "${product:=${OPZHUB_APP_NAME:-ManageMyOpz}}"
  : "${short:=${OPZHUB_SHORT:-opzhub}}"
  : "${version:=unknown}"

  printf '%s (%s)\n' "${product}" "${short}"
  printf '\nRelease (replaced on upgrade)\n'
  printf '  version:    %s\n' "${version}"
  printf '  build:      %s\n' "${build:-(none)}"
  printf '  built_at:   %s\n' "${built:-(none)}"
  printf '  home:       %s\n' "${OPZHUB_HOME}"
  if [ -r "${OPZHUB_HOME}/opzhub-be-app.jar" ]; then
    printf '  engine_jar: %s\n' "${OPZHUB_HOME}/opzhub-be-app.jar"
  else
    printf '  engine_jar: (not built yet)\n'
  fi

  printf '\nSite (kept across upgrades)\n'
  printf '  config:     %s\n' "${OPZHUB_CONFIG}"
  printf '  data:       %s\n' "${OPZHUB_DATA}"
  printf '  logs:       %s\n' "${OPZHUB_LOGS}"
  printf '  solution:   %s\n' "${OPZHUB_SOLUTION_ID:-(unset)}"

  printf '\nRuntime\n'
  printf '  user:       %s (%s)\n' "$(id -un)" "$(id -u)"
  printf '  mode:       %s\n' "$(opzhub_ctl_runtime)"
  if [ "$(opzhub_ctl_runtime)" = compose ]; then
    printf '  compose:    %s\n' "$(opzhub_ctl_compose_file 2>/dev/null || printf '%s' none)"
  fi

  printf '\nModules on disk\n'
  if [ -d "${OPZHUB_HOME}/modules" ]; then
    local m any=0
    for m in "${OPZHUB_HOME}/modules"/*; do
      [ -d "${m}" ] || continue
      any=1
      printf '  %s\n' "$(basename "${m}")"
    done
    if [ "${any}" -eq 0 ]; then
      printf '  (kernel only)\n'
    fi
  else
    printf '  (no modules/ directory)\n'
  fi

  printf '\nServices\n'
  printf '  %-8s %-10s %s\n' ID STATE DETAIL
  local id
  while IFS= read -r id; do
    [ -n "${id}" ] || continue
    printf '  '
    opzhub_ctl_status_one "${id}" || true
  done <<EOF
$(opzhub_ctl_ids_all)
EOF
}

opzhub_ctl_list() {
  cat <<'EOF'
Services:

  opzgw    public HTTPS gateway (nginx)
  opzgui   frontend SPA
  opzbe    Java backend
  opzpy    Python API and workers (if AI/OCR, mail, or face/fingerprint is present)

Usage:
  opzhubctl start  [all|NAME...]
  opzhubctl stop   [all|NAME...]
  opzhubctl restart [all|NAME...]
  opzhubctl status [all|NAME...]
  opzhubctl logs   NAME [-f] [-n LINES]
  opzhubctl release
  opzhubctl version
  opzhubctl list
EOF
}
