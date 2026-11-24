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
  "$src/NativeCharacterMemory.kt" "$src/EmbeddedChatTurn.kt" "$src/ModelAdmission.kt" "$src/InferencePreset.kt" "$src/ResidentModel.kt" tests/EmbeddedChatTurnSmoke.kt -include-runtime -d "$tmp/turn.jar"
"$java_home/bin/java" -Xcheck:jni -Djava.library.path="$tmp" -jar "$tmp/turn.jar"

# Independent character memory regression.
kotlinc "$src/NativeConversation.kt" "$src/NativeCharacterMemory.kt" tests/NativeCharacterMemorySmoke.kt -include-runtime -d "$tmp/memory.jar"
"$java_home/bin/java" -jar "$tmp/memory.jar"

# Evidence-bound relationship assessment regression (no real model).
kotlinc "$src/NativeConversation.kt" "$src/NativeCharacterMemory.kt" "$src/NativeChatWorkspace.kt" \
  "$src/RelationshipAssessment.kt" tests/RelationshipAssessmentSmoke.kt -include-runtime -d "$tmp/assessment.jar"
"$java_home/bin/java" -jar "$tmp/assessment.jar"

# Extractive memory assessment regression (no real model).
kotlinc "$src/NativeConversation.kt" "$src/NativeCharacterMemory.kt" "$src/NativeChatWorkspace.kt" \
  "$src/RelationshipAssessment.kt" "$src/MemoryAssessment.kt" tests/MemoryAssessmentSmoke.kt -include-runtime -d "$tmp/memory-assessment.jar"
"$java_home/bin/java" -jar "$tmp/memory-assessment.jar"

# Memory format compatibility and weighted retrieval.
kotlinc "$src/NativeConversation.kt" "$src/NativeCharacterMemory.kt" tests/NativeMemoryScaleSmoke.kt -include-runtime -d "$tmp/memory-scale.jar"
"$java_home/bin/java" -jar "$tmp/memory-scale.jar"

# Conservative model admission policy.
kotlinc "$src/ModelAdmission.kt" tests/ModelAdmissionSmoke.kt -include-runtime -d "$tmp/admission.jar"
"$java_home/bin/java" -jar "$tmp/admission.jar"

# Device inference preset validation.
kotlinc "$src/ModelAdmission.kt" "$src/InferencePreset.kt" tests/InferencePresetSmoke.kt -include-runtime -d "$tmp/preset.jar"
"$java_home/bin/java" -jar "$tmp/preset.jar"

# Model residency lifecycle (isolated JNI test double).
kotlinc "$src/EmbeddedLlama.kt" "$src/EmbeddedTextSink.kt" "$src/NativeModelLibrary.kt" \
  "$src/NativeConversation.kt" "$src/ResidentModel.kt" tests/ResidentModelSmoke.kt -include-runtime -d "$tmp/resident.jar"
"$java_home/bin/java" -Xcheck:jni -Djava.library.path="$tmp" -jar "$tmp/resident.jar"
