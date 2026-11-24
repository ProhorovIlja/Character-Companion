#include <jni.h>
#include "whisper.h"
#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
std::once_flag logs;
void silent(ggml_log_level, const char *, void *) {}
void require(bool ok) { if (!ok) throw std::runtime_error("Speech recognition failed"); }
void fail(JNIEnv * env) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) { env->ThrowNew(cls, "Local speech recognition failed"); env->DeleteLocalRef(cls); }
}
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_caicompanion_app_EmbeddedWhisper_nativeTranscribeV1(JNIEnv * env, jobject,
    jbyteArray path, jshortArray pcm, jboolean russian, jint threads) {
    try {
        require(path && pcm && threads >= 1 && threads <= 8);
        const int length = env->GetArrayLength(path), count = env->GetArrayLength(pcm);
        require(length > 0 && length <= 16384 && count > 0 && count <= 2880000);
        std::string name(length, '\0');
        env->GetByteArrayRegion(path, 0, length, reinterpret_cast<jbyte *>(name.data()));
        require(!env->ExceptionCheck() && name.find('\0') == std::string::npos);
        std::call_once(logs, [] { whisper_log_set(silent, nullptr); });
        auto config = whisper_context_default_params();
        config.use_gpu = false;
        std::unique_ptr<whisper_context, decltype(&whisper_free)> ctx(
            whisper_init_from_file_with_params(name.c_str(), config), whisper_free);
        require(ctx && whisper_is_multilingual(ctx.get()));
        std::vector<float> audio(count);
        jshort chunk[4096];
        for (int offset = 0; offset < count;) {
            const int n = std::min(4096, count - offset);
            env->GetShortArrayRegion(pcm, offset, n, chunk);
            require(!env->ExceptionCheck());
            for (int i = 0; i < n; ++i) audio[offset + i] = chunk[i] / 32768.0f;
            offset += n;
        }
        auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = threads;
        params.language = russian ? "ru" : "en";
        params.translate = false;
        params.detect_language = false;
        params.no_context = true;
        params.no_timestamps = true;
        params.print_special = params.print_progress = params.print_realtime = params.print_timestamps = false;
        require(whisper_full(ctx.get(), params, audio.data(), count) == 0);
        const int segments = whisper_full_n_segments(ctx.get());
        require(segments > 0 && segments <= 4096);
        std::string result;
        for (int i = 0; i < segments; ++i) {
            const char * text = whisper_full_get_segment_text(ctx.get(), i);
            require(text != nullptr);
            size_t n = 0;
            while (n <= 64000 - result.size() && text[n]) ++n;
            require(n <= 64000 - result.size());
            result.append(text, n);
        }
        require(!result.empty());
        auto bytes = env->NewByteArray(static_cast<jsize>(result.size()));
        require(bytes != nullptr);
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(result.size()), reinterpret_cast<const jbyte *>(result.data()));
        require(!env->ExceptionCheck());
        return bytes;
    } catch (...) { fail(env); return nullptr; }
}
