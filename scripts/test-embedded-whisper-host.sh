#!/usr/bin/env bash
# Explicit developer invocation; fixture never ships in the app.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Linux ]]; then echo "Linux host required" >&2; exit 1; fi
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
java_home="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
src=app/src/main/java/dev/caicompanion/app
clang++ -std=c++17 -shared -fPIC -Wall -Wextra -Werror \
  -Itests/fixtures/jni_whisper -I"$java_home/include" -I"$java_home/include/linux" \
  app/src/main/cpp/whisper/mandy_whisper.cpp -o "$tmp/libmandy_whisper.so"
kotlinc "$src/EmbeddedWhisper.kt" "$src/MandyLanguage.kt" "$src/NativeConversation.kt" \
  tests/EmbeddedWhisperSmoke.kt -include-runtime -d "$tmp/smoke.jar"
java -Xcheck:jni -Djava.library.path="$tmp" -jar "$tmp/smoke.jar"
