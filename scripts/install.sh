#!/usr/bin/env bash
# Собрать CleanMySaluteTV, поставить на ТВ и применить cleanmysalutetv.conf. Можно запускать повторно.
#
#   bash scripts/install.sh                 собрать из исходников, поставить, применить настройки
#   bash scripts/install.sh --apk FILE      поставить готовый APK (свою же сборку)
#   bash scripts/install.sh --config-only   только применить cleanmysalutetv.conf, без установки
#
# Порядок из cleanyourtv: штатный лаунчер трогаем только после того, как новый поставлен и запустился.
. "$(dirname "$0")/lib.sh"
need_tv

MODE=build APK=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apk) MODE=file; APK="$2"; shift ;;
    --config-only) MODE=none ;;
    *) die "неизвестный параметр $1" ;;
  esac
  shift
done

bash "$ROOT/scripts/preflight.sh" || die "Предварительные проверки не прошли. Исправь пункты с ✗ и запусти снова."
[ -f "$STATE/stock_home.txt" ] || bash "$ROOT/scripts/backup.sh"   # без бэкапа ТВ не меняем

# ---------------------------------------------------------------- 1. сборка
if [ "$MODE" = build ]; then
  bash "$ROOT/scripts/build.sh" || die "Сборка не удалась"
  APK="$ROOT/launcher/app/build/outputs/apk/release/app-release.apk"
fi

# ---------------------------------------------------------------- 2. установка
if [ "$MODE" != none ]; then
  [ -f "$APK" ] || die "APK не найден: $APK"
  say "Устанавливаю"
  # push + pm install: 'adb install' на Android 14 может зависнуть в ожидании подтверждения на экране
  a push "$APK" /data/local/tmp/cmst.apk >/dev/null
  out=$(sh_ pm install -r /data/local/tmp/cmst.apk 2>&1); sh_ rm -f /data/local/tmp/cmst.apk
  echo "$out"
  case "$out" in
    *Success*) ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*) die "Стоит CleanMySaluteTV с другой подписью. Удали его: adb -s $TV uninstall $PKG" ;;
    *INSTALL_FAILED_OLDER_SDK*) die "Android на ТВ старее minSdk лаунчера (26)." ;;
    *) die "Установка не удалась (см. выше). Штатный лаунчер не тронут." ;;
  esac
fi
sh_ pm list packages "$PKG" | grep -q "$PKG" || die "CleanMySaluteTV не установлен."

# ---------------------------------------------------------------- 3. разрешения
sh_ pm grant $PKG android.permission.ACCESS_FINE_LOCATION 2>/dev/null   # имя Wi-Fi в кнопке сети

# ---------------------------------------------------------------- 4. тема: config.json и обои
say "Применяю cleanmysalutetv.conf"
sh_ am force-stop $PKG
sh_ am start -W -n $HOME_ACT >/dev/null 2>&1; sleep 2          # создаёт папку файлов лаунчера
cfg="$STATE/config.json"
CMST_DOCK="${DOCK:-}" CMST_HIDDEN_APPS="${HIDDEN_APPS:-}" CMST_APP_LABELS="${APP_LABELS:-}" \
CMST_LIVE_TV_PACKAGE="${LIVE_TV_PACKAGE:-}" CMST_GREETING_NAME="${GREETING_NAME:-}" \
CMST_COLUMNS="${COLUMNS_PER_ROW:-}" CMST_APP_LIBRARY="${APP_LIBRARY:-yes}" \
CMST_SHOW_STATS="${SHOW_STATS:-no}" CMST_REDUCED_MOTION="${REDUCED_MOTION:-no}" \
CMST_SCENES="${SCENES:-yes}" CMST_SCENE_SECONDS="${SCENE_SECONDS:-}" CMST_SCENE_LIST="${SCENE_LIST:-}" \
CMST_WEATHER="${WEATHER:-yes}" CMST_WEATHER_CITY="${WEATHER_CITY:-}" \
CMST_WEATHER_LAT="${WEATHER_LAT:-}" CMST_WEATHER_LON="${WEATHER_LON:-}" \
  python3 -I "$ROOT/scripts/make_config.py" "$cfg" "$ROOT/config.extra.json" || die "Не удалось собрать config.json"
put_theme_file "$cfg" config.json || die "config.json не записан на ТВ"

WP="${WALLPAPER:-}"
if [ -n "$WP" ] && [ "$WP" != none ]; then
  [ -f "$WP" ] || die "Обои не найдены: $WP"
  case "$WP" in *.jpg|*.jpeg|*.JPG|*.JPEG) ;; *) die "Обои должны быть в JPEG (wallpaper.jpg)" ;; esac
  put_theme_file "$WP" wallpaper.jpg || die "обои не записаны на ТВ"
else
  del_theme_file wallpaper.jpg
fi
rev="$STATE/theme_revision"; date +%s > "$rev"
put_theme_file "$rev" theme_revision >/dev/null || true       # лаунчер перечитывает тему при новой ревизии

# ---------------------------------------------------------------- 5. сделать домашним экраном
say "Делаю CleanMySaluteTV главным экраном"
st=$(sh_ am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $HOME_ACT 2>&1 | tr -d '\r')
case "$st" in *"Status: ok"*) ;; *) die "Лаунчер не запустился, штатный лаунчер не тронут:
$st" ;; esac
sh_ cmd package set-home-activity --user 0 $HOME_ACT >/dev/null
if yes_ "${DISABLE_STOCK_LAUNCHER:-no}"; then
  for p in $KNOWN_STOCK_LAUNCHERS; do
    sh_ pm list packages -e "$p" | tr -d '\r' | grep -qx "package:$p" && dis "$p"
  done
fi
echo "HOME сейчас ведёт на: $(home_resolved)"
[ "$(home_resolved)" = "$HOME_ACT" ] || warn "Кнопка Home всё ещё открывает другой лаунчер. Попробуй DISABLE_STOCK_LAUNCHER=yes (см. docs/SBER.md)."

# ---------------------------------------------------------------- 6. заставка
case "${SCREENSAVER:-clock}" in
  clock)
    sh_ settings put secure screensaver_components $DREAM
    sh_ settings put secure screensaver_enabled 1
    echo "заставка: часы CleanMySaluteTV" ;;
  original)
    orig=$(grep '^screensaver_components=' "$STATE/screensaver_orig.txt" 2>/dev/null | cut -d= -f2-)
    [ -n "$orig" ] && [ "$orig" != null ] && sh_ settings put secure screensaver_components "$orig"
    sh_ settings put secure screensaver_enabled 1
    echo "заставка: исходная (${orig:-по умолчанию})" ;;
  off)
    sh_ settings put secure screensaver_enabled 0
    echo "заставка: выключена" ;;
  *) warn "Неизвестное SCREENSAVER='$SCREENSAVER' (clock, original или off)" ;;
esac

# ---------------------------------------------------------------- 7. DNS с блокировкой рекламы (по желанию)
if yes_ "${AD_BLOCK_DNS:-no}"; then
  sh_ settings put global private_dns_specifier "${AD_BLOCK_DNS_HOST:-dns.adguard-dns.com}"
  sh_ settings put global private_dns_mode hostname
  echo "частный DNS: ${AD_BLOCK_DNS_HOST:-dns.adguard-dns.com}"
fi

# ---------------------------------------------------------------- 8. показать
key KEYCODE_HOME; sleep 4
shot install_home
say "Готово. Не забудь выключить отладку по USB, когда закончишь."
