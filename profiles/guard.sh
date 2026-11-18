# CleanMySaluteTV: проверка перед отключением пакетов. Запускается НА ТЕЛЕВИЗОРЕ (sh из Android):
#   adb push profiles/guard.sh /data/local/tmp/cmst_guard.sh
#   adb shell sh /data/local/tmp/cmst_guard.sh <пакет> [<пакет>...]
# На каждый пакет одна строка: "OK <пакет>" или "NO <пакет> <причина>". Так её зовут scripts/lib.sh
# (guard_check) и приложение для Mac (TVStore.guardCheck).
#
# Защиты перенесены из tools/disable.sh проекта Gathin23/cleanyourtv (MIT), где каждая появилась после
# реальной поломки: ТВ не загружался, оставался без главного экрана, входы HDMI показывали «Нет сигнала»,
# пропадали Wi-Fi или Настройки.

# Ядро Android и промежуточный слой ТВ: без них ТВ может не загрузиться (cleanyourtv #884, #1578, #2557, #2803).
CORE_RE='^(android|android\.ext\.(services|shared)|com\.(google\.)?android\.sdksandbox|com\.android\.location\.fused|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*|com\.cltv\.mal|com\.mediatek\.(tis|tv\.service|tv\.agent))$'
# Поставщики данных, которые ни одной функции ТВ не нужны: их отключать можно.
CORE_OK='^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$'
SETUP=com.google.android.tungsten.setupwraith

# Имена пакетов из строк вида "  пакет/.Activity".
names() { sed -n -E 's#^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+)/.*#\1#p' | sort -u; }
has() { printf '%s\n' "$1" | grep -qxF "$2"; }

# Кто отвечает на Home, без заглушек: FallbackHome, мастер настройки, Настройки, заводские тесты.
LAUNCHERS=$(cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
  | grep -v '/[^ ]*FallbackHome' | names \
  | grep -viE 'factorytest|factory_test|factorymode' \
  | grep -vxF -e "$SETUP" -e com.android.tv.settings -e com.android.settings -e com.android.provision)
# Лаунчеры, которые останутся, когда отключится всё перечисленное.
REMAIN=$LAUNCHERS
for p in "$@"; do REMAIN=$(printf '%s\n' "$REMAIN" | grep -vxF "$p"); done

# Чем ТВ открывает входы и каналы, и чем открывает Настройки.
INPUT_APPS=$(cmd package query-activities --brief -a android.intent.action.VIEW -t vnd.android.cursor.item/channel 2>/dev/null | names)
SETTINGS_APPS=$(cmd package query-activities --brief -a android.settings.SETTINGS 2>/dev/null | names)

for p in "$@"; do
  if printf '%s\n' "$p" | grep -qE "$CORE_RE" && ! printf '%s\n' "$p" | grep -qE "$CORE_OK"; then
    echo "NO $p часть Android или ТВ: без него телевизор может не загрузиться"; continue
  fi
  if has "$LAUNCHERS" "$p" && [ -z "$REMAIN" ]; then
    echo "NO $p последний лаунчер: без него кнопке Home нечего открыть. Сначала сделайте главным экраном CleanMySaluteTV"; continue
  fi
  if [ "$p" = "$SETUP" ]; then
    owners=$(dumpsys wifi 2>/dev/null | grep -o 'cname=[^ ]*')
    if [ -z "$owners" ]; then
      echo "NO $p не удалось проверить, кому принадлежит сохранённая сеть Wi-Fi"; continue
    elif printf '%s\n' "$owners" | grep -q setupwraith; then
      echo "NO $p сеть Wi-Fi сохранена мастером настройки: Android удалит её. Забудьте сеть и подключитесь заново в Настройках"; continue
    fi
  fi
  if has "$INPUT_APPS" "$p"; then
    echo "NO $p через него ТВ открывает входы HDMI и каналы"; continue
  fi
  if has "$SETTINGS_APPS" "$p"; then
    echo "NO $p через него открываются Настройки"; continue
  fi
  # Служба входов HDMI/AV/тюнера просит TV_INPUT_HARDWARE. Без неё входы видят кабель, но показывают
  # «Нет сигнала», и вернуть картинку помогает только полный откат и холодная перезагрузка (cleanyourtv #321).
  # TvInputService по имени не проверяем: его объявляют и обычные приложения (VLC, онлайн-кино) для превью.
  if dumpsys package "$p" 2>/dev/null | sed -n -E '/requested permissions:/,/(install|runtime) permissions:|User [0-9]+:/p' | grep -q TV_INPUT_HARDWARE; then
    echo "NO $p служба входов HDMI/AV/тюнера: без неё входы покажут «Нет сигнала»"; continue
  fi
  echo "OK $p"
done
