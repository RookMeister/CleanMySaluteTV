# shellcheck shell=bash
# Общие функции скриптов CleanMySaluteTV. Подключается, а не запускается:  . "$(dirname "$0")/lib.sh"
# Основа — scripts/lib.sh из sridathballeda/cleantv (MIT), переделано под лаунчер из Gathin23/cleanyourtv.

set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE="$ROOT/state"                       # файлы конкретного ТВ: бэкапы, список отключённого, скриншоты (в git не идут)
mkdir -p "$STATE/screens" "$STATE/backup"
PKG=com.rookmeister.cleanmysalutetv
HOME_ACT=$PKG/.MainActivity
DREAM=$PKG/.ClockDream
DISABLED_LOG="$STATE/disabled_by_us.txt"
# Штатные лаунчеры, которые install.sh умеет узнавать (Sber первым).
KNOWN_STOCK_LAUNCHERS="ru.sberdevices.starlauncher com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.android.tv.launcher"

say()  { printf '\033[1m== %s\033[0m\n' "$*"; }
warn() { printf '\033[33m!! %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[31mxx %s\033[0m\n' "$*" >&2; exit 1; }

find_adb() {
  command -v adb 2>/dev/null && return
  local c
  for c in "${ANDROID_HOME:-}/platform-tools/adb" "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
           "$HOME/Library/Android/sdk/platform-tools/adb" "$HOME/Android/Sdk/platform-tools/adb" \
           /opt/homebrew/bin/adb /usr/local/bin/adb; do
    [ -x "$c" ] && { printf '%s\n' "$c"; return; }
  done
}
ADB="${ADB:-$(find_adb)}"
[ -n "$ADB" ] || die "adb не найден. Поставь: brew install --cask android-platform-tools"

# cleanmysalutetv.conf (синтаксис shell KEY=value). Начни с копии cleanmysalutetv.conf.example.
CONF="${CMST_CONF:-$ROOT/cleanmysalutetv.conf}"
if [ -f "$CONF" ]; then
  # shellcheck disable=SC1090
  . "$CONF"
fi
TV="${TV:-${TV_IP:-}}"
[ -n "$TV" ] && case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac

need_tv() { [ -n "$TV" ] || die "Укажи TV_IP в cleanmysalutetv.conf (найти: bash scripts/find_tv.sh)."; }

yes_() { case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in yes|y|true|1|on|да) return 0 ;; esac; return 1; }

# Обёртки adb. stdin из /dev/null, чтобы adb не съедал остаток скрипта.
a()   { "$ADB" -s "$TV" "$@" </dev/null; }
sh_() { "$ADB" -s "$TV" shell "$@" </dev/null; }

# Скриншот в state/screens/<name>.png. Сначала в файл: некоторые ТВ пишут отладку в stdout и портят
# "exec-out screencap -p". Картинка с HDMI и тюнера выходит чёрной.
shot() {
  sh_ screencap -p /sdcard/_cmst.png >/dev/null 2>&1
  a pull /sdcard/_cmst.png "$STATE/screens/$1.png" >/dev/null && echo "скриншот: state/screens/$1.png"
  sh_ rm -f /sdcard/_cmst.png
}

key() { local k; for k in "$@"; do sh_ input keyevent "$k"; sleep 0.4; done; }

# Пакеты, которые нельзя отключать никогда (profiles/never.txt). Проверяется в dis().
is_never() { grep -qxF "$1" <(grep -vE '^\s*(#|$)' "$ROOT/profiles/never.txt" | awk '{print $1}'); }

# Отключить ОДИН пакет (один pm на вызов adb shell: цепочка pm может молча остановиться после первого)
# и записать его, чтобы restore.sh мог вернуть.
dis() {
  local p="$1" out
  if is_never "$p"; then warn "отказ: $p в profiles/never.txt"; return 1; fi
  out=$(sh_ pm disable-user --user 0 "$p" 2>&1)
  echo "$p: $out"
  case "$out" in
    *disabled-user*) grep -qxF "$p" "$DISABLED_LOG" 2>/dev/null || echo "$p" >> "$DISABLED_LOG" ;;
    *) return 1 ;;
  esac
}

# Кто сейчас обрабатывает HOME.
home_handlers() {
  sh_ cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME \
    | grep -E '^ +packageName=' | sed 's/.*packageName=//' | tr -d '\r' | sort -u
}
home_resolved() {
  sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r'
}

# Записать файл темы в папку лаунчера. Сначала через ThemeProvider лаунчера (файл создаётся от имени
# лаунчера; на Android 13 файл, созданный shell через cp, лаунчер потом не может открыть), потом cp.
# $1 — локальный файл, $2 — имя в папке темы (config.json, wallpaper.jpg, theme_revision).
THEME_DIR=/sdcard/Android/data/$PKG/files
THEME_URI=content://$PKG.theme
put_theme_file() {
  local src="$1" name="$2" tmp="/data/local/tmp/cmst-${2//\//-}" out
  a push "$src" "$tmp" >/dev/null || return 1
  out=$(sh_ "rm -f $THEME_DIR/$name 2>/dev/null; o=\$(content write --uri $THEME_URI/$name < $tmp 2>&1); case \"\$o\" in *'Unsupported operation'*|*'Error while accessing provider'*|*Exception*) mkdir -p $THEME_DIR && cp $tmp $THEME_DIR/$name && echo via-cp || echo FAILED ;; *) echo via-provider ;; esac; rm -f $tmp")
  out=$(printf '%s' "$out" | tr -d '\r' | tail -1)
  [ "$out" = FAILED ] && { warn "не удалось записать $name на ТВ"; return 1; }
  echo "  $name ($out)"
}
del_theme_file() { sh_ "rm -f $THEME_DIR/$1 2>/dev/null; content delete --uri $THEME_URI/$1 2>/dev/null" >/dev/null; }
