#!/bin/sh
# dockerize-proof.sh — build and run every container recipe the product
# generates, against a real daemon.
#
#   scripts/dockerize-proof.sh <dir>
#
# <dir> is what DockerizeProofFixturesTest wrote:
#
#   mvn -B -pl rack -am test -Dtest=DockerizeProofFixturesTest \
#       -Dnmox.dockerize.out=<dir> -Dsurefire.failIfNoSpecifiedTests=false \
#       -Dspotbugs.skip=true -Djacoco.skip=true
#
# one small real project per recipe (proof-node, proof-go, ...), each holding
# exactly the Dockerfile, .dockerignore, compose file and nginx conf that
# Docker Panel > Dockerize writes for a project of that kind, plus the
# "PHP Web (LEMP)" template with the stack it ships. proof.list names them:
#
#   <id> <folder> <image|compose|idle> <port> <second path that must be 200, or ->
#
# "A Dockerfile that was never built is a claim, not a product" (v1.353.0).
# That release built each one by hand, once. This is the same proof standing:
# the Dockerize proof workflow runs it on a runner every week, on demand, and
# on a push to a proof/** branch, because the base images move and a tag that
# exists is not an image that builds.
#
# Per recipe:
#   image    docker build, run detached on a port docker picks on 127.0.0.1,
#            GET / until 200 (at most 30 s), then the second path once
#   compose  the compose file AS WRITTEN, published port included (the PHP
#            recipe is php-fpm behind an nginx sidecar and speaks no HTTP of
#            its own; the LEMP template is three images): config, build,
#            up -d, then the same two requests
#   idle     the "no toolchain detected" file: built, and still up after 3 s
#
# One line per recipe, in this form and in <dir>/verdicts.txt:
#
#   PROOF <id>: build=<rc> run=<rc> http=<code> deep=<code|n/a> seconds=<n>
#
# ("-" where a step was not reached.) Exit 0 only when every recipe passed.
#   2  usage, or <dir> holds no proof.list, or there is no curl
#   3  docker is not running
#   1  a recipe failed, or something this run made could not be removed
#
# What it removes is what it made, and nothing else: containers and images
# carry a label of this run and are removed by that label; a compose stack
# gets a project name of this run and is taken down by that name. It never
# prunes. Base images it pulled stay, as they would after any build.
#
# NMOX_PROOF_ONLY="go rust"   prove only these ids
# NMOX_PROOF_HTTP_WAIT=10     wait less than 30 s for the first 200
# NMOX_PROOF_STEP_LIMIT=900   seconds one build or one `up` may take, where
#                             timeout(1) exists (it does on a runner)
set -u

OUT="${1:-}"
if [ -z "$OUT" ]; then
  echo "usage: scripts/dockerize-proof.sh <dir written by DockerizeProofFixturesTest>"
  exit 2
fi
if [ ! -f "$OUT/proof.list" ]; then
  echo "dockerize-proof: no proof.list in $OUT - generate the projects first (see this script's header)"
  exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "dockerize-proof: curl is needed to ask the containers for a page"
  exit 2
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "dockerize-proof: docker is not running; this proof needs a daemon (there is no docker on PATH)"
  exit 3
fi
if ! docker info >/dev/null 2>&1; then
  echo "dockerize-proof: docker is not running; this proof needs a daemon (docker info failed)"
  exit 3
fi

OUT="$(cd "$OUT" && pwd)"
LOGS="$OUT/logs"
mkdir -p "$LOGS"
VERDICTS="$OUT/verdicts.txt"
: > "$VERDICTS"

RUN_ID="$(date +%s)-$$"
LABEL="org.nmox.dockerize-proof=$RUN_ID"
ONLY="${NMOX_PROOF_ONLY:-}"
STEP_LIMIT="${NMOX_PROOF_STEP_LIMIT:-900}"
HTTP_WAIT="${NMOX_PROOF_HTTP_WAIT:-30}"
case "$HTTP_WAIT" in
  ''|*[!0-9]*) HTTP_WAIT=30 ;;
esac
[ "$HTTP_WAIT" -gt 30 ] && HTTP_WAIT=30

docker version > "$LOGS/docker-version.txt" 2>&1

# the stack that is up right now, for the trap: "<project> <dir>" or empty
STACK_PROJECT=""
STACK_DIR=""
FAILED=""
PROVEN=0

# One step, stopped when it outlives its limit where the machine can do that.
bounded() {
  if command -v timeout >/dev/null 2>&1; then
    timeout "$STEP_LIMIT" "$@"
  else
    "$@"
  fi
}

# GET until 200 or until HTTP_WAIT seconds have passed; prints the last code.
await_200() {
  a_url="$1"
  a_deadline=$(( $(date +%s) + HTTP_WAIT ))
  a_code=000
  while :; do
    a_left=$(( a_deadline - $(date +%s) ))
    [ "$a_left" -le 0 ] && break
    a_max=3
    [ "$a_left" -lt 3 ] && a_max="$a_left"
    a_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time "$a_max" "$a_url" 2>/dev/null)"
    [ "$a_code" = 200 ] && break
    sleep 1
  done
  printf '%s' "${a_code:-000}"
}

get_once() {
  g_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$1" 2>/dev/null)"
  printf '%s' "${g_code:-000}"
}

# Containers and images of THIS run, found by the label this run put on them.
remove_labelled() {
  r_ids="$(docker ps -aq --filter "label=$LABEL" 2>/dev/null)"
  if [ -n "$r_ids" ]; then
    # shellcheck disable=SC2086
    docker rm -fv $r_ids >/dev/null 2>&1 || echo "dockerize-proof: could not remove containers: $r_ids"
  fi
  r_imgs="$(docker images -q --filter "label=$LABEL" 2>/dev/null)"
  if [ -n "$r_imgs" ]; then
    # shellcheck disable=SC2086
    docker rmi -f $r_imgs >/dev/null 2>&1 || echo "dockerize-proof: could not remove images: $r_imgs"
  fi
}

# The compose stack of THIS run, found by the project name this run gave it:
# its containers, its network, its volumes and the images it built itself.
remove_stack() {
  if [ -n "$STACK_PROJECT" ] && [ -d "$STACK_DIR" ]; then
    ( cd "$STACK_DIR" && docker compose -p "$STACK_PROJECT" down -v --rmi local --remove-orphans ) >/dev/null 2>&1 \
      || echo "dockerize-proof: could not take down the stack $STACK_PROJECT"
  fi
  STACK_PROJECT=""
  STACK_DIR=""
}

on_exit() {
  remove_stack
  remove_labelled
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

show_log() {
  echo "---- last lines of $1"
  tail -n 40 "$1"
  echo "----"
}

prove_image() {
  # $1 id  $2 dir  $3 port, or - for the idle recipe
  p_tag="nmox-proof-$1:$RUN_ID"
  p_log="$LOGS/$1.log"
  bounded docker build --label "$LABEL" -t "$p_tag" "$2" > "$p_log" 2>&1
  V_BUILD=$?
  [ "$V_BUILD" -eq 0 ] || return 0

  if [ "$3" = "-" ]; then
    p_cid="$(docker run -d --label "$LABEL" "$p_tag" 2>> "$p_log")"
    V_RUN=$?
    [ "$V_RUN" -eq 0 ] || return 0
    sleep 3
    if [ "$(docker inspect -f '{{.State.Running}}' "$p_cid" 2>> "$p_log")" != "true" ]; then
      V_RUN=1
      docker logs "$p_cid" >> "$p_log" 2>&1
    fi
    return 0
  fi

  p_cid="$(docker run -d --label "$LABEL" -p "127.0.0.1::$3" "$p_tag" 2>> "$p_log")"
  V_RUN=$?
  [ "$V_RUN" -eq 0 ] || return 0
  p_bound="$(docker port "$p_cid" "$3/tcp" 2>> "$p_log")"
  # "127.0.0.1:49153"; only the first line, should docker ever print two
  # shellcheck disable=SC2086
  set -- "$1" "$2" "$3" $p_bound
  p_host="${4:-}"
  p_host="${p_host##*:}"
  if [ -z "$p_host" ]; then
    echo "docker published no host port for $3/tcp" >> "$p_log"
    V_RUN=1
    return 0
  fi
  V_HTTP="$(await_200 "http://127.0.0.1:$p_host/")"
  if [ "$V_HTTP" = 200 ] && [ "$V_DEEP" != "n/a" ]; then
    V_DEEP="$(get_once "http://127.0.0.1:$p_host$PATH_DEEP")"
  fi
  docker logs "$p_cid" >> "$p_log" 2>&1
  return 0
}

prove_compose() {
  # $1 id  $2 dir  $3 the port the compose file publishes
  c_log="$LOGS/$1.log"
  STACK_PROJECT="nmoxproof-$RUN_ID-$1"
  STACK_DIR="$2"
  ( cd "$2" && docker compose -p "$STACK_PROJECT" config -q ) > "$c_log" 2>&1
  V_BUILD=$?
  [ "$V_BUILD" -eq 0 ] || return 0
  ( cd "$2" && bounded docker compose -p "$STACK_PROJECT" build ) >> "$c_log" 2>&1
  V_BUILD=$?
  [ "$V_BUILD" -eq 0 ] || return 0
  ( cd "$2" && bounded docker compose -p "$STACK_PROJECT" up -d ) >> "$c_log" 2>&1
  V_RUN=$?
  if [ "$V_RUN" -eq 0 ]; then
    V_HTTP="$(await_200 "http://127.0.0.1:$3/")"
    if [ "$V_HTTP" = 200 ] && [ "$V_DEEP" != "n/a" ]; then
      V_DEEP="$(get_once "http://127.0.0.1:$3$PATH_DEEP")"
    fi
  fi
  ( cd "$2" && docker compose -p "$STACK_PROJECT" ps -a && docker compose -p "$STACK_PROJECT" logs --no-color ) >> "$c_log" 2>&1
  return 0
}

while read -r id folder mode port deep <&3; do
  [ -n "$id" ] || continue
  if [ -n "$ONLY" ]; then
    case " $ONLY " in
      *" $id "*) ;;
      *) continue ;;
    esac
  fi
  dir="$OUT/$folder"
  started="$(date +%s)"
  V_BUILD="-"
  V_RUN="-"
  V_HTTP="-"
  V_DEEP="n/a"
  PATH_DEEP=""
  if [ "$deep" != "-" ] && [ -n "$deep" ]; then
    V_DEEP="-"
    PATH_DEEP="$deep"
  fi
  [ "$mode" = idle ] && V_HTTP="n/a"

  if [ ! -d "$dir" ]; then
    echo "dockerize-proof: $id: no project at $dir"
    V_BUILD=missing
  else
    case "$mode" in
      image)   prove_image "$id" "$dir" "$port" ;;
      idle)    prove_image "$id" "$dir" "-" ;;
      compose) prove_compose "$id" "$dir" "$port" ;;
      *)       echo "dockerize-proof: $id: no such way to prove a recipe: $mode"; V_BUILD=unknown ;;
    esac
  fi
  remove_stack
  remove_labelled

  seconds=$(( $(date +%s) - started ))
  verdict="PROOF $id: build=$V_BUILD run=$V_RUN http=$V_HTTP deep=$V_DEEP seconds=$seconds"
  echo "$verdict"
  echo "$verdict" >> "$VERDICTS"
  PROVEN=$(( PROVEN + 1 ))

  ok=yes
  [ "$V_BUILD" = 0 ] || ok=no
  [ "$V_RUN" = 0 ] || ok=no
  [ "$V_HTTP" = 200 ] || [ "$V_HTTP" = "n/a" ] || ok=no
  [ "$V_DEEP" = 200 ] || [ "$V_DEEP" = "n/a" ] || ok=no
  if [ "$ok" = no ]; then
    FAILED="$FAILED $id"
    [ -f "$LOGS/$id.log" ] && show_log "$LOGS/$id.log"
  fi
done 3< "$OUT/proof.list"

if [ -f "$OUT/no-recipe.txt" ]; then
  none=""
  while read -r kind <&3; do
    none="$none $kind"
  done 3< "$OUT/no-recipe.txt"
  echo "PROOF no-recipe:$none (each is handed the file 'generic' proves)"
fi

# what is left of this run after its own removals: there should be nothing
left_containers="$(docker ps -aq --filter "label=$LABEL" 2>/dev/null)"
left_images="$(docker images -q --filter "label=$LABEL" 2>/dev/null)"
if [ -n "$left_containers$left_images" ]; then
  echo "PROOF cleanup: left behind containers=[$left_containers] images=[$left_images]"
  FAILED="$FAILED cleanup"
else
  echo "PROOF cleanup: nothing of this run is left"
fi

if [ "$PROVEN" -eq 0 ]; then
  echo "dockerize-proof: FAIL - proof.list named no recipe to prove"
  exit 1
fi
if [ -n "$FAILED" ]; then
  echo "dockerize-proof: FAIL -$FAILED ($PROVEN proven; logs in $LOGS)"
  exit 1
fi
echo "dockerize-proof: PASS - $PROVEN recipes built and answered"
exit 0
