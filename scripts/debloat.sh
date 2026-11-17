#!/usr/bin/env bash
# Disable packages in small batches, screenshotting Home and the input menu after each batch so a
# load-bearing package can be pinned to its batch. Nothing is uninstalled; restore.sh re-enables all.
#   bash scripts/debloat.sh <list-file> [--batch N] [--dry-run]
# list-file: one package per line, '#' comments allowed (build it from state/inspect/report.tsv and the
# owner's answers). Packages in profiles/never.txt are always refused.
. "$(dirname "$0")/lib.sh"
need_tv

LIST="" BATCH=4 DRY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --batch) BATCH="$2"; shift ;;
    --dry-run) DRY=1 ;;
    *) LIST="$1" ;;
  esac
  shift
done
[ -f "$LIST" ] || die "usage: scripts/debloat.sh <list-file> [--batch N] [--dry-run]"

# (no mapfile: macOS ships bash 3.2)
pkgs=()
while IFS= read -r line; do pkgs+=("$line"); done < <(grep -vE '^\s*(#|$)' "$LIST" | awk '{print $1}' | tr -d '\r')
installed=$(sh_ pm list packages | sed 's/package://' | tr -d '\r')

n=0 b=1
for p in "${pkgs[@]}"; do
  grep -qxF "$p" <<<"$installed" || { echo "$p: not installed, skipping"; continue; }
  if [ "$DRY" = 1 ]; then is_never "$p" && echo "$p: REFUSED (never list)" || echo "$p: would disable"; continue; fi
  dis "$p" || true
  n=$((n + 1))
  if [ $((n % BATCH)) -eq 0 ]; then
    say "Batch $b done: checking Home and the input menu still work"
    key KEYCODE_HOME; sleep 3; shot "debloat_batch${b}_home"
    key KEYCODE_TV_INPUT; sleep 2; shot "debloat_batch${b}_input"; key KEYCODE_BACK
    b=$((b + 1))
  fi
done
if [ "$DRY" = 0 ] && [ $((n % BATCH)) -ne 0 ]; then
  say "Batch $b done"; key KEYCODE_HOME; sleep 3; shot "debloat_batch${b}_home"
fi
[ "$DRY" = 1 ] || say "Disabled packages are logged in state/disabled_by_us.txt (restore.sh re-enables them)"
