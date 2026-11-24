#pragma once
// TEST DOUBLE ONLY. Never use as an actual recognition backend.
#include <cassert>
#include <string>
enum ggml_log_level { GGML_LOG_LEVEL_NONE };
enum whisper_sampling_strategy { WHISPER_SAMPLING_GREEDY };
struct whisper_context { std::string language; int mode = 0; };
struct whisper_context_params { bool use_gpu = true; };
struct whisper_full_params { int n_threads; const char * language; bool translate, detect_language, no_context, no_timestamps, print_special, print_progress, print_realtime, print_timestamps; };
inline int live = 0;
inline void whisper_log_set(void (*)(ggml_log_level, const char *, void *), void *) {}
inline whisper_context_params whisper_context_default_params() { return {}; }
inline whisper_context * whisper_init_from_file_with_params(const char * path, whisper_context_params p) {
    assert(!p.use_gpu && live == 0); ++live;
    auto * c = new whisper_context;
    if (std::string(path).find("english-only") != std::string::npos) c->mode = -1;
    return c;
}
inline void whisper_free(whisper_context * c) { --live; delete c; }
inline int whisper_is_multilingual(whisper_context * c) { return c->mode != -1; }
inline whisper_full_params whisper_full_default_params(whisper_sampling_strategy) { return {}; }
inline int whisper_full(whisper_context * c, whisper_full_params p, const float * audio, int count) {
    assert(p.n_threads >= 1 && p.n_threads <= 8 && !p.translate && !p.detect_language && p.no_context && p.no_timestamps);
    assert(!p.print_special && !p.print_progress && !p.print_realtime && !p.print_timestamps);
    c->language = p.language;
    assert(c->language == "en" || c->language == "ru");
    if (count == 1) { c->mode = 1; return -1; }
    assert(audio[0] == -1.f && audio[1] == 32767.f / 32768.f);
    c->mode = count == 3 ? 2 : count == 4 ? 3 : count == 5 ? 4 : 0;
    return 0;
}
inline int whisper_full_n_segments(whisper_context * c) { return c->mode == 2 ? 0 : 2; }
inline const char * whisper_full_get_segment_text(whisper_context * c, int i) {
    if (c->mode == 3) return "\xc0\xaf";
    if (c->mode == 4) { static std::string huge(64001, 'a'); return huge.c_str(); }
    return i == 0 ? (c->language == "ru" ? u8" Привет" : " Hello") : u8" 🌙 ";
}
