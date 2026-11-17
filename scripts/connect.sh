#!/usr/bin/env bash
# Connect to the TV over the network and wait until it's authorised.
#   bash scripts/connect.sh [IP]
. "$(dirname "$0")/lib.sh"
[ $# -ge 1 ] && TV="$1" && case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
need_tv

out=$("$ADB" connect "$TV" 2>&1 </dev/null); echo "$out"
case "$out" in
  *refused*) die "ТВ отклонил подключение: отладка по USB выключена. Настройки → Для разработчиков → Отладка по USB (и отладка по сети, если есть). Если меню закрыто — docs/SBER.md." ;;
  *"No route"*|*"timed out"*|*"cannot connect"*) die "Не достучаться до $TV. ТВ включён и в ТОЙ ЖЕ сети Wi-Fi, что и компьютер (не гостевой, без VPN)? macOS: см. AGENTS.md, раздел 0." ;;
esac
state=$("$ADB" -s "$TV" get-state 2>&1 </dev/null)
if [ "$state" != device ]; then
  say "Посмотри на ТВ: «Разрешить отладку по USB?» → «Всегда разрешать с этого компьютера» → OK."
  for _ in $(seq 1 60); do
    sleep 2
    "$ADB" connect "$TV" >/dev/null 2>&1 </dev/null
    state=$("$ADB" -s "$TV" get-state 2>&1 </dev/null)
    [ "$state" = device ] && break
  done
fi
[ "$state" = device ] || die "ТВ не разрешил отладку ($state). Подтверди запрос на ТВ и запусти снова."

say "Подключено"
sh_ 'echo "  модель:  $(getprop ro.product.manufacturer) $(getprop ro.product.model)"; echo "  android: $(getprop ro.build.version.release) (API $(getprop ro.build.version.sdk))"; echo "  плата:   $(getprop ro.board.platform), $(getprop ro.product.cpu.abi)"; wm size | sed "s/^/  /"'
echo "  салют тв: $(sh_ pm list packages ru.sberdevices.starlauncher | grep -q starlauncher && echo да || echo нет)"
