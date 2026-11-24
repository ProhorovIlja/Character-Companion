#pragma once
// TEST DOUBLE ONLY: never include in Android or production builds.
#include <cassert>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
using llama_token = int32_t;
enum ggml_log_level { GGML_LOG_LEVEL_NONE };
struct llama_vocab {};
struct llama_model { llama_vocab vocab; };
struct llama_context { uint32_t n; int mode = -1; int sampled = 0; };
struct llama_sampler { std::vector<llama_sampler *> children; };
struct llama_model_params { int n_gpu_layers; bool check_tensors; };
struct llama_context_params { uint32_t n_ctx, n_batch, n_ubatch, n_seq_max; int n_threads, n_threads_batch; bool offload_kqv, op_offload; };
struct llama_chat_message { const char * role; const char * content; };
struct llama_batch { llama_token * tokens; int count; };
inline int live_contexts = 0, live_samplers = 0;
inline int expected_context = 0, expected_threads = 0;
inline int fitting_probes = 0;
inline void llama_log_set(void (*)(ggml_log_level, const char *, void *), void *) {}
inline void llama_backend_init() {}
inline llama_model_params llama_model_default_params() { return {}; }
inline llama_model * llama_model_load_from_file(const char *, llama_model_params p) { assert(p.n_gpu_layers == 0 && p.check_tensors); return new llama_model; }
inline void llama_model_free(llama_model * p) { assert(live_contexts == 0 && live_samplers == 0); delete p; }
inline bool llama_model_has_encoder(const llama_model *) { return false; }
inline bool llama_model_has_decoder(const llama_model *) { return true; }
inline const char * llama_model_chat_template(const llama_model *, const char *) { return "fixture"; }
inline int llama_chat_apply_template(const char *, const llama_chat_message * chat, size_t n, bool add, char * out, int capacity) {
    assert(add && n >= 2 && std::string(chat[0].role) == "system");
    std::string text = chat[n-1].content;
    if (text == "bad-template") return -1;
    if (text == "fit-context" || text == "fit-template" || text == "fit-required" || text == "fit-negative") {
        assert(std::string(chat[0].content) == u8"Мэнди 🌙");
        if (n > 4) {
            if (text == "fit-template") return capacity;
            text = text == "fit-negative" ? "overflow" : "fit-overflow";
        } else {
            assert(n == 2 || (n == 4 && std::string(chat[1].role) == "user" && std::string(chat[1].content) == "recent" && std::string(chat[2].content) == "recent-reply"));
            text = text == "fit-required" ? "fit-overflow" : "fit-ok";
        }
    }
    if (text == "fit-maximal" || text == "fit-single") {
        assert(std::string(chat[0].content) == u8"Мэнди 🌙");
        assert(std::string(chat[1].role) == "user");
        const size_t target = text == "fit-maximal" ? 6 : 2;
        text = n > target ? "fit-overflow" : n == target ? "large-piece" : "fit-ok";
    }
    if (text == "fit-many") {
        if (n == 201) fitting_probes = 0;
        assert(++fitting_probes <= 9);
        text = n > 77 ? "fit-overflow" : n == 77 ? "large-piece" : "fit-ok";
    }
    if (text == "history") assert(n == 4 && std::string(chat[1].content) == "earlier" && std::string(chat[2].content) == "reply");
    if (text == "unicode-input") assert(std::string(chat[0].content) == u8"Мэнди 🌙");
    assert(text.size() < static_cast<size_t>(capacity));
    std::memcpy(out, text.data(), text.size()); return static_cast<int>(text.size());
}
inline const llama_vocab * llama_model_get_vocab(const llama_model * p) { return &p->vocab; }
inline int llama_tokenize(const llama_vocab *, const char * text, int n, llama_token * out, int capacity, bool, bool) {
    std::string s(text, n);
    expected_context = s == "profile-low" ? 2048 : s == "profile-quality" ? 6144 : 0;
    expected_threads = s == "profile-low" ? 2 : s == "profile-quality" ? 6 : 0;
    if (s == "overflow") return -capacity - 1;
    if (s == "fit-overflow") return capacity;
    out[0] = s == "decode-error" ? 91 : s == "limit" ? 92 : s == "bad-utf8" ? 93 : s == "empty" ? 94 : s == "large-piece" ? 95 : 90;
    return 1;
}
inline int llama_model_n_ctx_train(const llama_model *) { return 8192; }
inline llama_context_params llama_context_default_params() { return {}; }
inline llama_context * llama_init_from_model(llama_model *, llama_context_params p) { assert(live_contexts == 0); assert(p.n_threads >= 1 && p.n_threads <= 8 && p.n_threads == p.n_threads_batch); if (expected_context) assert(p.n_ctx == static_cast<uint32_t>(expected_context) && p.n_threads == expected_threads); ++live_contexts; return new llama_context{p.n_ctx}; }
inline void llama_free(llama_context * p) { --live_contexts; delete p; }
inline uint32_t llama_n_ctx(const llama_context * p) { return p->n; }
inline llama_batch llama_batch_get_one(llama_token * p, int n) { return {p, n}; }
inline int llama_decode(llama_context * p, llama_batch b) { assert(b.count == 1); if (p->mode < 0) p->mode = b.tokens[0]; return p->mode == 91 ? -1 : 0; }
inline llama_sampler * new_sampler() { ++live_samplers; return new llama_sampler; }
inline int llama_sampler_chain_default_params() { return 0; }
inline llama_sampler * llama_sampler_chain_init(int) { return new_sampler(); }
inline void llama_sampler_chain_add(llama_sampler * p, llama_sampler * child) { p->children.push_back(child); }
inline llama_sampler * llama_sampler_init_top_k(int) { return new_sampler(); }
inline llama_sampler * llama_sampler_init_top_p(float, size_t) { return new_sampler(); }
inline llama_sampler * llama_sampler_init_temp(float) { return new_sampler(); }
inline llama_sampler * llama_sampler_init_dist(uint32_t) { return new_sampler(); }
inline void llama_sampler_free(llama_sampler * p) { for (auto * c : p->children) llama_sampler_free(c); --live_samplers; delete p; }
inline llama_token llama_sampler_sample(llama_sampler *, llama_context * p, int) {
    if (p->mode == 94) return 0;
    if (p->mode == 92) return 1;
    if (p->mode == 93) return p->sampled++ == 0 ? 4 : 0;
    if (p->mode == 95) return p->sampled++ == 0 ? 5 : 0;
    return p->sampled < 3 ? ++p->sampled : 0;
}
inline bool llama_vocab_is_eog(const llama_vocab *, llama_token t) { return t == 0; }
inline int llama_token_to_piece(const llama_vocab *, llama_token t, char * out, int cap, int, bool) {
    std::string s = t == 1 ? "Hello " : t == 2 ? "\xf0\x9f" : t == 3 ? "\x8c\x99" : t == 4 ? "\xc0\xaf" : std::string(300, 'x');
    if (cap < static_cast<int>(s.size())) return -static_cast<int>(s.size());
    std::memcpy(out, s.data(), s.size()); return static_cast<int>(s.size());
}
