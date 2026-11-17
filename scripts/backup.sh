#!/usr/bin/env bash
# Save the TV's state before changing anything: package lists, disabled packages, getprop, all settings,
# HOME handlers and the current screensaver. Goes to state/backup/<date-time>/ (never committed).
#   bash scripts/backup.sh
. "$(dirname "$0")/lib.sh"
need_tv

B="$STATE/backup/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$B"
sh_ pm list packages -f -i -U  > "$B/packages_full.txt"
sh_ pm list packages -s        > "$B/packages_system.txt"
sh_ pm list packages -3        > "$B/packages_third_party.txt"
sh_ pm list packages -d        > "$B/packages_disabled.txt"
sh_ getprop                    > "$B/getprop.txt"
for ns in global secure system; do sh_ settings list $ns > "$B/settings_$ns.txt"; done
sh_ cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME > "$B/home_activities.txt"
home_resolved > "$B/home_resolved.txt"

# first backup also records what restore.sh should put back
[ -f "$STATE/stock_home.txt" ]        || cp "$B/home_resolved.txt" "$STATE/stock_home.txt"
[ -f "$STATE/screensaver_orig.txt" ]  || {
  echo "screensaver_components=$(sh_ settings get secure screensaver_components | tr -d '\r')"
  echo "screensaver_enabled=$(sh_ settings get secure screensaver_enabled | tr -d '\r')"
} > "$STATE/screensaver_orig.txt"
[ -f "$STATE/dns_orig.txt" ] || {
  echo "private_dns_mode=$(sh_ settings get global private_dns_mode | tr -d '\r')"
  echo "private_dns_specifier=$(sh_ settings get global private_dns_specifier | tr -d '\r')"
} > "$STATE/dns_orig.txt"

say "Backup saved to ${B#$ROOT/}"
wc -l "$B"/*.txt | tail -1
