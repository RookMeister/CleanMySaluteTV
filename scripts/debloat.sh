#!/usr/bin/env bash
# Disable packages in small batches, screenshotting Home and the input menu after each batch so a
# load-bearing package can be pinned to its batch. Nothing is uninstalled; restore.sh re-enables all.
#   bash scripts/debloat.sh <list-file> [--batch N] [--dry-run]
# list-file: one package per line, '#' comments allowed (build it from state/inspect/report.tsv and the
# owner's answers). Packages in profiles/never.txt are always refused, and so is anything profiles/guard.sh
# refuses on the TV (core Android, the last launcher, the HDMI inputs, Settings, the setup wizard's Wi-Fi).
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
[ -f "$LIST" ] || die "использование: scripts/debloat.sh <список> [--batch N] [--dry-run]"

# (no mapfile: macOS ships bash 3.2)
pkgs=()
while IFS= read -r line; do pkgs+=("$line"); done < <(grep -vE '^\s*(#|$)' "$LIST" | awk '{print $1}' | tr -d '\r')
installed=$(sh_ pm list packages | sed 's/package://' | tr -d '\r')

# Что из списка стоит на ТВ, и что из этого можно отключать (profiles/guard.sh, одним вызовом).
present=()
for p in "${pkgs[@]}"; do
  grep -qxF "$p" <<<"$installed" && present+=("$p") || echo "$p: не установлен, пропускаю"
done
[ ${#present[@]} -gt 0 ] || die "Ничего из списка на ТВ нет"
verdicts=$(guard_check "${present[@]}") || die "Не удалось проверить список на ТВ: ничего не отключено"

n=0 b=1
for p in "${present[@]}"; do
  v=$(grep -E "^(OK|NO) $p( |$)" <<<"$verdicts" | head -1)
  case "$v" in
    OK*) ;;
    NO*) echo "$p: ОТКАЗ — ${v#NO $p }"; continue ;;
    *) echo "$p: ОТКАЗ — проверка не ответила"; continue ;;
  esac
  if [ "$DRY" = 1 ]; then is_never "$p" && echo "$p: ОТКАЗ (в never.txt)" || echo "$p: будет отключён"; continue; fi
  dis "$p" checked || true
  n=$((n + 1))
  if [ $((n % BATCH)) -eq 0 ]; then
    say "Пачка $b готова: проверяю, что Home и меню входов работают"
    key KEYCODE_HOME; sleep 3; shot "debloat_batch${b}_home"
    key KEYCODE_TV_INPUT; sleep 2; shot "debloat_batch${b}_input"; key KEYCODE_BACK
    b=$((b + 1))
  fi
done
if [ "$DRY" = 0 ] && [ $((n % BATCH)) -ne 0 ]; then
  say "Пачка $b готова"; key KEYCODE_HOME; sleep 3; shot "debloat_batch${b}_home"
fi
[ "$DRY" = 1 ] || say "Отключённые пакеты записаны в state/disabled_by_us.txt (restore.sh включит их обратно)"
