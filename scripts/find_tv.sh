#!/usr/bin/env bash
# Find Android TVs on the local network with network debugging (ADB, port 5555) open.
#   bash scripts/find_tv.sh
# Checks every address in the ARP table. If nothing shows up, read the TV's IP in
# Settings > Network & Internet > (your Wi-Fi) and put it in cleanmysalutetv.conf.
. "$(dirname "$0")/lib.sh"

port_open() {   # $1 = ip; 1-second timeout on every OS
  if command -v timeout >/dev/null; then timeout 1 bash -c "exec 3<>/dev/tcp/$1/5555" 2>/dev/null
  elif command -v nc >/dev/null; then nc -z -w 1 "$1" 5555 >/dev/null 2>&1
  else bash -c "exec 3<>/dev/tcp/$1/5555" 2>/dev/null; fi
}

say "Ищу в сети телевизоры с отладкой по сети (порт 5555)"
ips=$(arp -a 2>/dev/null | grep -oE '([0-9]{1,3}\.){3}[0-9]{1,3}' | grep -vE '\.(255|0)$|^(224|239|255)\.' | sort -u)
found=0
for ip in $ips; do
  if port_open "$ip"; then
    "$ADB" connect "$ip:5555" >/dev/null 2>&1 </dev/null
    model=$("$ADB" -s "$ip:5555" shell getprop ro.product.model </dev/null 2>/dev/null | tr -d '\r')
    echo "  $ip  ${model:-(подтверди запрос на ТВ, чтобы увидеть модель)}"
    found=1
  fi
done
if [ "$found" = 0 ]; then
  warn "ТВ не найден. Проверь на телевизоре:"
  echo "   1. Он в ТОЙ ЖЕ сети, что и компьютер (не гостевая, не точка доступа; VPN на паузе)."
  echo "   2. Меню разработчика включено: Настройки → Об устройстве → 7 раз OK на «Сборка» (если закрыто — docs/SBER.md)."
  echo "   3. Для разработчиков → Отладка по USB (и отладка по сети, если есть) включена."
  echo "   4. ТВ не спит. Запусти снова или впиши его IP (Настройки → Сеть) в cleanmysalutetv.conf."
fi
