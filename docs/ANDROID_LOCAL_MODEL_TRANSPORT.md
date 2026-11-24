# Mandy AI — Android local model transport (2026-11-24)

Native Kotlin/JVM HTTP transport added to the common Android source set. LocalChatProvider uses Android org.json to create OpenAI-compatible chat requests and decode content deltas from llama-server. LocalModelStream consumes LF/CRLF SSE, multiline data, ignores comments, validates UTF-8, requires normal finish plus DONE and a nonblank reply. It returns completed text separately from transient displayed fragments. No external hosts, proxy or redirect; fixed IPv4 loopback. No new Gradle dependency or change to app manifest, cleartext policy or legacy navigation. Caller must supply an explicitly trusted and authorized local server and execute synchronously on a user-initiated worker. No server shipped, native JNI engine, activity integration, history integration, API authentication or cloud fallback. An unauthenticated loopback service can be impersonated by another local process; do not send private chat to untrusted endpoint. Android network security behavior and API compatibility need real device verification.

Limits: request 256 KiB, response 2 MiB, line 128 KiB, answer 16,000 UTF-16 units, connect 5s, read 15s, cooperative 120s deadline checked during reads. Blocking operations can outlast deadline/cancel until return or timeout; this is not immediate voice barge-in. Only display content, no tool calling. Model output-length truncation fails explicitly rather than committing it as a complete answer. Kotlin transport smoke fixture injects a simple decoder; Android org.json binding is not compiled or tested in this environment.

Verification: exact LocalModelStream.kt and tests/LocalModelStreamSmoke.kt compiled with installed Kotlin/JVM 1.9.0 and Java 21, 13 checks passed with real JDK loopback HTTP fixture. One deprecation warning for Java URL(String) constructor on JDK 21; retained for Android compatibility. Test command:
kotlinc app/src/main/java/dev/caicompanion/app/LocalModelStream.kt tests/LocalModelStreamSmoke.kt -include-runtime -d /tmp/mandy-stream-smoke.jar
java -jar /tmp/mandy-stream-smoke.jar

References:
- https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md
- https://developer.android.com/privacy-and-security/security-config

No third-party code copied. No actual model weights downloaded, Android SDK build or Windows build, no new APK/EXE. Next: managed JNI engine or authenticated in-app endpoint, native chat persistence, UI integration and device tests. No scheduled jobs or automatic actions.
