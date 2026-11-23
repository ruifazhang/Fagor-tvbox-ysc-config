#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="$(ls -d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
AJ="$(ls "$ANDROID_HOME"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)"
D8="${BT%/}/d8"
test -x "$D8" && test -f "$AJ"
rm -rf "$ROOT/classes" "$ROOT/dex"; mkdir -p "$ROOT/classes" "$ROOT/dex"
find "$ROOT/src" "$ROOT/stub" -name '*.java' > "$ROOT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -classpath "$AJ" -d "$ROOT/classes" @"$ROOT/sources.txt"
find "$ROOT/classes/com/github/catvod/spider" -name '*.class' > "$ROOT/biz.txt"
"$D8" --min-api 21 --lib "$AJ" --output "$ROOT/dex" $(cat "$ROOT/biz.txt")
(cd "$ROOT/dex" && jar cf "$ROOT/fagor-spider.jar" classes.dex)
sha256sum "$ROOT/fagor-spider.jar"
