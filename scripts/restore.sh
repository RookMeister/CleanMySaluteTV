#!/usr/bin/env bash
# Undo CleanMySaluteTV: re-enable every package in state/disabled_by_us.txt (newest first), make the original
# launcher home again, put the original screensaver and DNS back.
#   bash scripts/restore.sh               keep CleanMySaluteTV installed (you can still pick it as home later)
#   bash scripts/restore.sh --uninstall   also remove CleanMySaluteTV
. "$(dirname "$0")/lib.sh"
need_tv
"$ADB" connect "$TV" >/dev/null </dev/null

if [ -f "$DISABLED_LOG" ]; then
  say "Включаю отключённые пакеты обратно"
  # newest first (stock launcher before the packages disabled earlier); tail -r is the macOS tac
  tac "$DISABLED_LOG" 2>/dev/null > "$STATE/.restore_order" || tail -r "$DISABLED_LOG" > "$STATE/.restore_order"
  while read -r p; do
    [ -n "$p" ] && echo "$p: $(sh_ pm enable --user 0 "$p")"
  done < "$STATE/.restore_order"
  rm -f "$STATE/.restore_order"
  mv "$DISABLED_LOG" "$DISABLED_LOG.restored-$(date +%Y%m%d-%H%M%S)"
fi

stock=$(cat "$STATE/stock_home.txt" 2>/dev/null | tr -d '\r')
[ -n "$stock" ] && [ "$stock" != "$HOME_ACT" ] && sh_ cmd package set-home-activity "$stock"

if [ -f "$STATE/screensaver_orig.txt" ]; then
  comp=$(grep '^screensaver_components=' "$STATE/screensaver_orig.txt" | cut -d= -f2-)
  en=$(grep '^screensaver_enabled=' "$STATE/screensaver_orig.txt" | cut -d= -f2-)
  [ -n "$comp" ] && [ "$comp" != null ] && sh_ settings put secure screensaver_components "$comp"
  [ -n "$en" ] && [ "$en" != null ] && sh_ settings put secure screensaver_enabled "$en"
fi

if [ -f "$STATE/dns_orig.txt" ]; then
  mode=$(grep '^private_dns_mode=' "$STATE/dns_orig.txt" | cut -d= -f2-)
  host=$(grep '^private_dns_specifier=' "$STATE/dns_orig.txt" | cut -d= -f2-)
  [ -z "$mode" ] || [ "$mode" = null ] && mode=opportunistic
  sh_ settings put global private_dns_mode "$mode"
  if [ -n "$host" ] && [ "$host" != null ]; then sh_ settings put global private_dns_specifier "$host"
  else sh_ settings delete global private_dns_specifier >/dev/null; fi
fi

[ "${1:-}" = --uninstall ] && sh_ pm uninstall $PKG
key KEYCODE_HOME
say "Восстановлено. Home ведёт на: $(home_resolved)"
