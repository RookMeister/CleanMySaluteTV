#!/usr/bin/env bash
# Builds mac/build/CleanMySaluteTV.app: the SwiftUI app, with adb, the clean-up profile and the launcher's APK inside,
# so nothing else is needed on the Mac.
#
#   bash mac/build.sh            the app, with the launcher APK from launcher/app/build (built first if missing)
#   bash mac/build.sh --open     the same, then opens it
set -euo pipefail
MAC="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$MAC/.." && pwd)"
APP="$MAC/build/CleanMySaluteTV.app"
APK="$ROOT/launcher/app/build/outputs/apk/release/app-release.apk"

say() { printf '\033[1m== %s\033[0m\n' "$*"; }
die() { printf '\033[31mxx %s\033[0m\n' "$*" >&2; exit 1; }

say "Собираю приложение"
(cd "$MAC" && swift build -c release --arch arm64 --arch x86_64) || die "swift build не прошёл"
BIN="$(cd "$MAC" && swift build -c release --arch arm64 --arch x86_64 --show-bin-path)/CleanMySaluteTV"

say "Собираю CleanMySaluteTV.app"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/CleanMySaluteTV"

VERSION=$(grep -m1 "versionName" "$ROOT/launcher/app/build.gradle" | sed "s/.*'\(.*\)'.*/\1/")
cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>CleanMySaluteTV</string>
  <key>CFBundleDisplayName</key><string>CleanMySaluteTV</string>
  <key>CFBundleIdentifier</key><string>com.rookmeister.cleanmysalutetv.mac</string>
  <key>CFBundleExecutable</key><string>CleanMySaluteTV</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>${VERSION}</string>
  <key>CFBundleVersion</key><string>${VERSION}</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  <key>CFBundleDevelopmentRegion</key><string>ru</string>
  <key>LSMinimumSystemVersion</key><string>26.0</string>
  <key>LSApplicationCategoryType</key><string>public.app-category.utilities</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSLocalNetworkUsageDescription</key>
  <string>Чтобы найти телевизор в домашней сети и управлять им по Wi-Fi.</string>
</dict>
</plist>
PLIST

# adb: the platform-tools binary, resolved through Homebrew's link (Apache 2.0, see NOTICE)
ADB_SRC=""
for c in /opt/homebrew/bin/adb /usr/local/bin/adb "$HOME/Library/Android/sdk/platform-tools/adb"; do
  [ -x "$c" ] && { ADB_SRC="$(python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$c")"; break; }
done
[ -n "$ADB_SRC" ] || die "Нет adb: brew install android-platform-tools"
cp "$ADB_SRC" "$APP/Contents/Resources/adb"

cp "$ROOT/profiles/sber-staros.tsv" "$ROOT/profiles/never.txt" "$APP/Contents/Resources/"
# the window's backdrop: one of the launcher's scenes (Liquid Glass floats over it)
cp "$ROOT/launcher/app/src/main/assets/scenes/scene-valley.jpg" "$APP/Contents/Resources/Backdrop.jpg"
# the Home screen step's preview: the scenes (not their -glass blur or cut-outs) and the clock's font
for f in "$ROOT"/launcher/app/src/main/assets/scenes/scene-*.jpg; do
  case "$f" in *-glass.jpg) ;; *) cp "$f" "$APP/Contents/Resources/" ;; esac
done
cp "$ROOT/launcher/app/src/main/assets/scenes/antonio_light.ttf" "$APP/Contents/Resources/"

if [ ! -f "$APK" ]; then
  say "APK лаунчера нет — собираю"
  bash "$ROOT/scripts/build.sh" || die "Сборка лаунчера не прошла"
fi
cp "$APK" "$APP/Contents/Resources/CleanMySaluteTV.apk"

[ -f "$MAC/AppIcon.icns" ] && cp "$MAC/AppIcon.icns" "$APP/Contents/Resources/AppIcon.icns"

# ad hoc signature: enough to run on this Mac (the bundled adb is signed with it too)
codesign --force --deep --sign - "$APP" >/dev/null 2>&1 || die "codesign не прошёл"

say "Готово: $APP ($(du -sh "$APP" | cut -f1))"
[ "${1:-}" = "--open" ] && open "$APP"
exit 0
