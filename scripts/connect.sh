#!/usr/bin/env bash
# Connect to the TV over the network and wait until it's authorised.
#   bash scripts/connect.sh [IP]
. "$(dirname "$0")/lib.sh"
[ $# -ge 1 ] && TV="$1" && case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
need_tv

out=$("$ADB" connect "$TV" 2>&1 </dev/null); echo "$out"
case "$out" in
  *refused*) die "The TV refused the connection: USB debugging is off. Settings > System > Developer options > USB debugging ON (and Network debugging, if shown)." ;;
  *"No route"*|*"timed out"*|*"cannot connect"*) die "Can't reach $TV. Is the TV awake and on the SAME Wi-Fi as this computer (no guest network, no VPN)? On macOS see AGENTS.md section 0." ;;
esac
state=$("$ADB" -s "$TV" get-state 2>&1 </dev/null)
if [ "$state" != device ]; then
  say "Look at the TV now: accept \"Allow USB debugging?\" (tick \"Always allow from this computer\")."
  for _ in $(seq 1 60); do
    sleep 2
    "$ADB" connect "$TV" >/dev/null 2>&1 </dev/null
    state=$("$ADB" -s "$TV" get-state 2>&1 </dev/null)
    [ "$state" = device ] && break
  done
fi
[ "$state" = device ] || die "TV not authorised ($state). Accept the prompt on the TV and run this again."

say "Connected"
sh_ 'echo "  model:   $(getprop ro.product.manufacturer) $(getprop ro.product.model)"; echo "  android: $(getprop ro.build.version.release) (API $(getprop ro.build.version.sdk))"; echo "  abi:     $(getprop ro.product.cpu.abi)"; wm size | sed "s/^/  /"'
echo "  google tv: $(sh_ pm list packages com.google.android.apps.tv.launcherx | grep -q launcherx && echo yes || echo no)"
