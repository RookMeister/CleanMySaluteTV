#!/usr/bin/env bash
# List every installed package with what the profiles know about it, and dump the components of
# packages nobody has classified yet (so you can tell what an oddly-named vendor package does).
#   bash scripts/inspect.sh            -> state/inspect/report.tsv and state/inspect/unknown/*.txt
#
# report.tsv columns: package  action  category  note
#   action: disable = safe to switch off, ask = owner's choice (streaming apps, casting, voice...),
#           keep = leave alone, never = the TV needs it, unknown = not in any profile: read unknown/<pkg>.txt
. "$(dirname "$0")/lib.sh"
need_tv

OUT="$STATE/inspect"; mkdir -p "$OUT/unknown"
known=$(cat "$ROOT"/profiles/*.tsv 2>/dev/null | grep -vE '^\s*(#|$)')
never=$(grep -vE '^\s*(#|$)' "$ROOT/profiles/never.txt" | awk '{print $1}')

printf 'package\taction\tcategory\tnote\n' > "$OUT/report.tsv"
for p in $(sh_ pm list packages | sed 's/package://' | tr -d '\r' | sort); do
  if grep -qxF "$p" <<<"$never"; then
    printf '%s\tnever\tessential\t%s\n' "$p" "$(grep -F "$p" "$ROOT/profiles/never.txt" | head -1 | cut -s -d'#' -f2- | sed 's/^ *//')" >> "$OUT/report.tsv"
    continue
  fi
  line=$(awk -F'\t' -v p="$p" '$1==p' <<<"$known" | head -1)
  if [ -n "$line" ]; then
    printf '%s\n' "$line" >> "$OUT/report.tsv"
  else
    printf '%s\tunknown\t\t\n' "$p" >> "$OUT/report.tsv"
    # components + intent actions: enough to tell telemetry, ads, key handlers and input menus apart
    sh_ dumpsys package "$p" > "$OUT/unknown/$p.full.txt" 2>/dev/null
    {
      echo "== $p  $(grep -m1 versionName "$OUT/unknown/$p.full.txt" | tr -d ' ')"
      grep -oE "$p/[A-Za-z0-9_.\$]+" "$OUT/unknown/$p.full.txt" | sort -u | sed 's/^/  /'
      echo "  actions: $(grep -oE 'Action: "[^"]+"' "$OUT/unknown/$p.full.txt" | sort -u | tr '\n' ' ')"
    } > "$OUT/unknown/$p.txt"
    rm -f "$OUT/unknown/$p.full.txt"
  fi
done

say "Отчёт: state/inspect/report.tsv"
awk -F'\t' 'NR>1{c[$2]++} END{for(k in c) printf "  %-8s %d\n", k, c[k]}' "$OUT/report.tsv"
echo "Неизвестные пакеты производителя (прочитай state/inspect/unknown/<pkg>.txt, прежде чем трогать):"
awk -F'\t' '$2=="unknown"{print "  "$1}' "$OUT/report.tsv" | grep -vE '^  (android|com\.android\.|com\.google\.android\.(ext|overlay|modulemetadata|permissioncontroller|providers))' || true
