# Mandy AI — host JNI boundary regression (2026-11-24)

Actual production-source mandy_llama.cpp (blob 4f770ed3a57c37a96e97376564080df820361b5c) was compiled into a temporary Linux shared library with Clang 17 and JDK 21 JNI headers. The existing Kotlin EmbeddedLlama, EmbeddedTextSink, NativeModelLibrary and NativeConversation were compiled unchanged. The JVM loaded the actual JNI bridge with -Xcheck:jni; 25 counted assertions passed twice, including a final run via scripts/test-embedded-jni-host.sh. No JNI warnings appeared in the captured output.

The native backend is a deliberately deterministic test double in tests/fixtures/jni_llama/llama.h, not upstream llama.cpp. It returns synthetic tokens, checks history and Unicode input, injects failures, and checks context and sampler lifetimes. It does not model the actual llama.cpp ABI, neural inference, memory use, tokenization, chat templates or sampling behavior. The test-only header is included solely by the host test command; production Android CMake and Gradle remain unchanged. Never distribute or use the fixture as a runtime.

Checks cover library loading, verified synthetic GGUF-shaped bytes, full message-array transfer, split UTF-8 output, template/token/decode failures, malformed UTF-8, empty reply, token-piece buffer growth, truncation, cooperative cancellation, propagation of Java callback exceptions, reentrant close rejection, repeated fresh contexts, cleanup, close/reopen and changed model bytes. Cleanup assertions exercise simulated allocations, not upstream memory leak detection. No Android-specific class or NDK build was exercised.

Reproduce explicitly on Linux with a JDK, Kotlin compiler and Clang:
bash scripts/test-embedded-jni-host.sh

The script builds in a temporary directory and cleans its own files. No downloads, workflow changes, CI dispatch, scheduled tasks or device installation. The environment still lacks Android SDK/NDK and GitHub DNS resolution; the connector exposes no new-workflow dispatch action. Existing manual build path remains unchanged. Real upstream compilation, actual trusted GGUF inference, Android packaging and device tests remain release gates. No APK or Windows executable produced.
