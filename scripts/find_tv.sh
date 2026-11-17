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

say "Looking for TVs with network debugging (port 5555) on this network"
ips=$(arp -a 2>/dev/null | grep -oE '([0-9]{1,3}\.){3}[0-9]{1,3}' | grep -vE '\.(255|0)$|^(224|239|255)\.' | sort -u)
found=0
for ip in $ips; do
  if port_open "$ip"; then
    "$ADB" connect "$ip:5555" >/dev/null 2>&1 </dev/null
    model=$("$ADB" -s "$ip:5555" shell getprop ro.product.model </dev/null 2>/dev/null | tr -d '\r')
    echo "  $ip  ${model:-(accept the prompt on the TV to see the model)}"
    found=1
  fi
done
if [ "$found" = 0 ]; then
  warn "No TV found. Check on the TV:"
  echo "   1. It's on the SAME Wi-Fi/router as this computer (not a guest network or hotspot; pause any VPN)."
  echo "   2. Developer options are on: Settings > System > About > press 'Android TV OS build' 7 times."
  echo "   3. Settings > System > Developer options > USB debugging (and Network debugging, if shown) is ON."
  echo "   4. The TV is awake. Then run this again, or put its IP (Settings > Network) in cleanmysalutetv.conf."
fi
