#!/usr/bin/env bash
# Проверить всё нужное до того, как трогать ТВ, и подсказать, как исправить проблему.
#   bash scripts/preflight.sh            код 0 = готово, 1 = есть что исправить (строки с ✗)
. "$(dirname "$0")/lib.sh"

ok=1
pass() { printf '  \033[32m✓\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$1"; shift; for l in "$@"; do printf '      %s\n' "$l"; done; ok=0; }
note() { printf '  \033[33m!\033[0m %s\n' "$*"; }

say "Проверки CleanMySaluteTV"

# 1. программы на компьютере
pass "adb: $ADB"
command -v python3 >/dev/null && pass "python3 есть" || fail "нет python3" "Нужен для сборки config.json: xcode-select --install"

# 2. файл настроек
if [ -f "$CONF" ]; then pass "cleanmysalutetv.conf найден"
else fail "нет cleanmysalutetv.conf" "cp cleanmysalutetv.conf.example cleanmysalutetv.conf   и впиши TV_IP (bash scripts/find_tv.sh)"; fi
if [ -z "$TV" ]; then
  fail "TV_IP не задан" "Найти: bash scripts/find_tv.sh, или на ТВ: Настройки → Сеть → твой Wi-Fi → IP-адрес"
  echo; exit 1
fi
ip="${TV%:*}"

# 3. одна сеть: сравниваем /24 телевизора с адресами компьютера
mine=$( { ifconfig 2>/dev/null | grep -E 'inet ' ; ip -4 addr 2>/dev/null | grep -E 'inet ' ; } \
        | grep -oE '([0-9]{1,3}\.){3}[0-9]{1,3}' | grep -vE '^(127\.|255\.)|\.255$' | sort -u)
if printf '%s\n' "$mine" | grep -q "^${ip%.*}\."; then
  pass "ТВ ($ip) и компьютер в одной сети"
else
  fail "ТВ ($ip) не похож на устройство из сети компьютера (у компьютера: $(printf '%s' "$mine" | tr '\n' ' '))" \
       "Подключи ТВ и компьютер к ОДНОМУ Wi-Fi/роутеру: не гостевая сеть, не точка доступа телефона. Выключи VPN."
fi

# 4. доступен и отладка включена (порт 5555)
reach=$("$ADB" connect "$TV" 2>&1 </dev/null)
case "$reach" in
  *refused*)
    fail "ТВ отвечает, но отладка ВЫКЛЮЧЕНА (connection refused)" \
         "Настройки → Настройки устройства → Об устройстве → 7 раз OK на «Сборка», затем Для разработчиков → Отладка по USB." \
         "Если меню разработчика не открывается (бывает на Салют ТВ), см. docs/SBER.md, раздел «Включить ADB»." ;;
  *"No route"*|*"timed out"*|*"cannot connect"*|*"unable"*)
    fail "Не достучаться до ТВ по $ip" \
         "ТВ включён (не в режиме ожидания)? Тот же Wi-Fi? IP верный (после перезагрузки может смениться)?" \
         "macOS: «No route to host» при открытом порте — это «Локальная сеть» в Конфиденциальности. Разреши её терминалу и перезапусти его." ;;
  *) pass "ТВ доступен, отладка включена" ;;
esac

# 5. разрешение на отладку
if [ "$ok" = 1 ]; then
  state=$("$ADB" -s "$TV" get-state 2>&1 </dev/null)
  case "$state" in
    device) pass "ТВ разрешил отладку ($(sh_ getprop ro.product.manufacturer | tr -d '\r') $(sh_ getprop ro.product.model | tr -d '\r'), Android $(sh_ getprop ro.build.version.release | tr -d '\r'))" ;;
    *unauthorized*) fail "ТВ спрашивает разрешение" \
         "На ТВ: «Разрешить отладку по USB?» → галочка «Всегда разрешать» → OK. Потом запусти снова (или scripts/connect.sh — он ждёт)." ;;
    *) fail "ТВ не готов: $state" "Запусти bash scripts/connect.sh" ;;
  esac
fi

note "Выключи отладку по USB, когда закончишь: на части Салют ТВ (ro.adb.secure=0) подключиться может любое устройство в сети."

echo
if [ "$ok" = 1 ]; then say "Все проверки пройдены"; exit 0; fi
say "Исправь пункты с ✗ и запусти снова: bash scripts/preflight.sh"; exit 1
