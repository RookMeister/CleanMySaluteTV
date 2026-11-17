#!/usr/bin/env bash
# Собрать APK лаунчера: launcher/app/build/outputs/apk/release/app-release.apk
# Нужны JDK 17 и Android SDK (platform 34, build-tools 34). На Mac:
#   brew install openjdk@17 && brew install --cask android-commandlinetools
#   sdkmanager --sdk_root=/opt/homebrew/share/android-commandlinetools "platforms;android-34" "build-tools;34.0.0"
#   bash scripts/build.sh [--test]     --test дополнительно гоняет JVM-тесты лаунчера
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/launcher"

if [ -z "${JAVA_HOME:-}" ]; then
  for j in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 "$(/usr/libexec/java_home -v 17 2>/dev/null || true)"; do
    [ -n "$j" ] && [ -x "$j/bin/jlink" ] && JAVA_HOME="$j" && break
  done
fi
[ -n "${JAVA_HOME:-}" ] || { echo "Нужен JDK 17: brew install openjdk@17" >&2; exit 1; }
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

if [ -z "${ANDROID_HOME:-}" ]; then
  for d in ~/Library/Android/sdk /opt/homebrew/share/android-commandlinetools /usr/local/share/android-commandlinetools ~/Android/Sdk; do
    [ -d "$d/platforms" ] && ANDROID_HOME="$d" && break
  done
fi
[ -n "${ANDROID_HOME:-}" ] || { echo "Android SDK не найден (см. комментарий в начале scripts/build.sh)" >&2; exit 1; }
export ANDROID_HOME
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

TASKS="assembleRelease"
[ "${1:-}" = --test ] && TASKS="testReleaseUnitTest assembleRelease"
# shellcheck disable=SC2086
./gradlew $TASKS -q --console=plain
ls -l app/build/outputs/apk/release/app-release.apk
