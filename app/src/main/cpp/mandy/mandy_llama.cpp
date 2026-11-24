#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
constexpr int MAX_PROMPT_BYTES = 256 * 1024;
std::once_flag backend_once;
void silent_log(ggml_log_level, const char *, void *) {}
void fail(JNIEnv * env, const char * text) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) { env->ThrowNew(cls, text); env->DeleteLocalRef(cls); }
}
void require(bool ok) {
    if (!ok) throw std::runtime_error("Native model operation failed");
}
std::string bytes(JNIEnv * env, jbyteArray array, int maximum) {
    require(array != nullptr);
    const jsize n = env->GetArrayLength(array);
    require(n > 0 && n <= maximum);
    std::string result(static_cast<size_t>(n), '\0');
    env->GetByteArrayRegion(array, 0, n, reinterpret_cast<jbyte *>(result.data()));
    require(!env->ExceptionCheck() && result.find('\0') == std::string::npos);
    return result;
}
std::string item(JNIEnv * env, jobjectArray array, int index, int maximum) {
    auto value = static_cast<jbyteArray>(env->GetObjectArrayElement(array, index));
    require(!env->ExceptionCheck());
    try {
        auto result = bytes(env, value, maximum);
        env->DeleteLocalRef(value);
        return result;
    } catch (...) {
        env->DeleteLocalRef(value);
        throw;
    }
}
struct Callbacks {
    JNIEnv * env;
    jobject sink;
    jmethodID stop;
    jmethodID emit;
    Callbacks(JNIEnv * e, jobject s) : env(e), sink(s) {
        require(s != nullptr);
        jclass cls = env->GetObjectClass(s);
        require(cls != nullptr);
        stop = env->GetMethodID(cls, "isCancelled", "()Z");
        emit = stop ? env->GetMethodID(cls, "onBytes", "([B)V") : nullptr;
        env->DeleteLocalRef(cls);
        require(stop && emit && !env->ExceptionCheck());
    }
    void check() {
        const auto cancelled = env->CallBooleanMethod(sink, stop);
        require(!env->ExceptionCheck() && !cancelled);
    }
    void output(const char * data, int n) {
        check();
        if (!n) return;
        require(n > 0 && n <= 65536);
        jbyteArray array = env->NewByteArray(n);
        require(array != nullptr);
        env->SetByteArrayRegion(array, 0, n, reinterpret_cast<const jbyte *>(data));
        if (!env->ExceptionCheck()) env->CallVoidMethod(sink, emit, array);
        env->DeleteLocalRef(array);
        require(!env->ExceptionCheck());
    }
};
using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_caicompanion_app_EmbeddedLlama_nativeLoad(JNIEnv * env, jobject, jbyteArray path) {
    try {
        auto name = bytes(env, path, 16384);
        std::call_once(backend_once, [] { llama_log_set(silent_log, nullptr); llama_backend_init(); });
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        params.check_tensors = true;
        Model model(llama_model_load_from_file(name.c_str(), params), llama_model_free);
        require(model && !llama_model_has_encoder(model.get()) && llama_model_has_decoder(model.get()));
        require(llama_model_chat_template(model.get(), nullptr) != nullptr);
        return static_cast<jlong>(reinterpret_cast<intptr_t>(model.release()));
    } catch (...) { fail(env, "Cannot load supported local model"); return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_dev_caicompanion_app_EmbeddedLlama_nativeClose(JNIEnv *, jobject, jlong handle) {
    if (handle) llama_model_free(reinterpret_cast<llama_model *>(static_cast<intptr_t>(handle)));
}
extern "C" JNIEXPORT void JNICALL
Java_dev_caicompanion_app_EmbeddedLlama_nativeGenerateV2(JNIEnv * env, jobject, jlong handle,
        jobjectArray roles, jobjectArray contents, jint context_tokens, jint output_tokens, jint seed, jint threads, jobject sink) {
    try {
        auto * model = reinterpret_cast<llama_model *>(static_cast<intptr_t>(handle));
        require(model && roles && contents && threads >= 1 && threads <= 8 && context_tokens >= 512 && context_tokens <= 8192 &&
                output_tokens > 0 && output_tokens <= 1024 && output_tokens < context_tokens);
        Callbacks cb(env, sink);
        cb.check();
        const int count = env->GetArrayLength(roles);
        require(count >= 2 && count <= 201 && env->GetArrayLength(contents) == count);
        std::vector<std::string> role, content;
        role.reserve(count); content.reserve(count);
        size_t total = 0;
        for (int i = 0; i < count; ++i) {
            role.push_back(item(env, roles, i, 16));
            content.push_back(item(env, contents, i, 65536));
            require(i == 0 ? role.back() == "system" : (role.back() == "user" || role.back() == "assistant"));
            total += content.back().size();
            require(total <= MAX_PROMPT_BYTES);
        }
        require(role.back() == "user");
        require(context_tokens <= llama_model_n_ctx_train(model));
        const char * tmpl = llama_model_chat_template(model, nullptr);
        require(tmpl != nullptr);
        auto * vocab = llama_model_get_vocab(model);
        require(vocab != nullptr);
        std::vector<char> prompt(MAX_PROMPT_BYTES);
        std::vector<llama_token> tokens(context_tokens);
        int prompt_tokens = 0;
        // Keep the complete system message and newest user message. Reduce only older history.
        // Geometric suffix reduction bounds template/tokenizer retries for long conversations.
        int first = 1;
        while (true) {
            cb.check();
            std::vector<llama_chat_message> chat;
            chat.reserve(count - first + 1);
            chat.push_back({role[0].c_str(), content[0].c_str()});
            for (int i = first; i < count; ++i) chat.push_back({role[i].c_str(), content[i].c_str()});
            int n = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, prompt.data(), prompt.size());
            require(n > 0);
            if (n < MAX_PROMPT_BYTES) {
                prompt_tokens = llama_tokenize(vocab, prompt.data(), n, tokens.data(), context_tokens, true, true);
                require(prompt_tokens != 0);
                if (prompt_tokens > 0 && prompt_tokens <= context_tokens - output_tokens) break;
            }
            if (first == count - 1) { fail(env, "Character context and latest message exceed model context"); return; }
            first += (count - first) / 2;
            // After trimming, do not leave an orphan assistant at the start of retained history.
            while (first < count - 1 && role[first] != "user") ++first;
        }
        cb.check();
        auto params = llama_context_default_params();
        params.n_ctx = context_tokens;
        params.n_batch = 256;
        params.n_ubatch = 128;
        params.n_seq_max = 1;
        params.n_threads = threads;
        params.n_threads_batch = threads;
        params.offload_kqv = false;
        params.op_offload = false;
        Context ctx(llama_init_from_model(model, params), llama_free);
        require(ctx && llama_n_ctx(ctx.get()) >= static_cast<uint32_t>(prompt_tokens + output_tokens));
        for (int offset = 0; offset < prompt_tokens;) {
            cb.check();
            int batch = std::min(128, prompt_tokens - offset);
            require(llama_decode(ctx.get(), llama_batch_get_one(tokens.data() + offset, batch)) == 0);
            offset += batch;
        }
        Sampler sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        require(sampler != nullptr);
        auto add = [&](llama_sampler * component) {
            require(component != nullptr);
            llama_sampler_chain_add(sampler.get(), component);
        };
        add(llama_sampler_init_top_k(40));
        add(llama_sampler_init_top_p(0.9f, 1));
        add(llama_sampler_init_temp(0.8f));
        add(llama_sampler_init_dist(static_cast<uint32_t>(seed)));
        std::vector<char> piece(256);
        for (int i = 0; i < output_tokens; ++i) {
            cb.check();
            llama_token token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) { cb.check(); return; }
            int len = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (len < 0) {
                require(len >= -65536);
                piece.resize(static_cast<size_t>(-len));
                len = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            require(len >= 0 && static_cast<size_t>(len) <= piece.size());
            cb.output(piece.data(), len);
            cb.check();
            require(llama_decode(ctx.get(), llama_batch_get_one(&token, 1)) == 0);
        }
        fail(env, "Output token limit reached before model completion");
    } catch (...) { fail(env, "Local generation failed or cancelled"); }
}
