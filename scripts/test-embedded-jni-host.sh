#!/usr/bin/env bash
# Explicit host regression test only. Never builds or packages an Android APK.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Linux ]]; then
  echo 'This host JNI test currently requires Linux.' >&2; exit 2
fi
java_home="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
compiler="${CXX:-clang++}"
command -v "$compiler" >/dev/null
command -v kotlinc >/dev/null
[[ -f "$java_home/include/jni.h" && -f "$java_home/include/linux/jni_md.h" ]]
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
src=app/src/main/java/dev/caicompanion/app
"$compiler" -std=c++17 -shared -fPIC -Wall -Wextra \
  -Itests/fixtures/jni_llama -I"$java_home/include" -I"$java_home/include/linux" \
  app/src/main/cpp/mandy/mandy_llama.cpp -o "$tmp/libmandy_llama.so"
kotlinc "$src/EmbeddedLlama.kt" "$src/EmbeddedTextSink.kt" "$src/NativeModelLibrary.kt" \
  "$src/NativeConversation.kt" tests/EmbeddedJniHostSmoke.kt -include-runtime -d "$tmp/host.jar"
"$java_home/bin/java" -Xcheck:jni -Djava.library.path="$tmp" -jar "$tmp/host.jar"

# Full durable turn integration, using the same isolated test-only native library.
kotlinc "$src/EmbeddedLlama.kt" "$src/EmbeddedTextSink.kt" "$src/NativeModelLibrary.kt" \
  "$src/NativeConversation.kt" "$src/NativeCharacterLibrary.kt" "$src/NativeChatWorkspace.kt" \
  "$src/EmbeddedChatTurn.kt" tests/EmbeddedChatTurnSmoke.kt -include-runtime -d "$tmp/turn.jar"
"$java_home/bin/java" -Xcheck:jni -Djava.library.path="$tmp" -jar "$tmp/turn.jar"
