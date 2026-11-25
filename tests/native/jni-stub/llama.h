#pragma once
// Host test double only: not llama.cpp headers or a model implementation.
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <vector>
using llama_token = int;
enum ggml_log_level { GGML_LOG_LEVEL_NONE };
struct llama_vocab {};
struct llama_model { llama_vocab vocab; const char* tmpl = "stub"; };
struct llama_context { unsigned limit; unsigned decoded = 0; };
struct llama_sampler { unsigned seed = 0; int sampled = 0; std::vector<llama_sampler*> children; };
struct llama_model_params { int n_gpu_layers; bool check_tensors; };
struct llama_context_params { unsigned n_ctx, n_batch, n_ubatch, n_seq_max; int n_threads, n_threads_batch; bool offload_kqv, op_offload; };
struct llama_sampler_chain_params {};
struct llama_chat_message { const char* role; const char* content; };
struct llama_batch { llama_token* tokens; int n; };
inline void llama_log_set(void(*)(ggml_log_level,const char*,void*),void*) {}
inline void llama_backend_init() {}
inline llama_model_params llama_model_default_params() { return {}; }
// The smoke test is single-owner: refuse a second load if cleanup leaked.
inline int live_models = 0;
inline llama_model* llama_model_load_from_file(const char* path,llama_model_params) { if (live_models != 0) return nullptr; auto p = new llama_model; ++live_models; if (std::strstr(path,"unsupported.gguf")) p->tmpl = "unsupported"; else if (std::strstr(path,"missing.gguf")) p->tmpl = nullptr; return p; }
inline void llama_model_free(llama_model* p) { --live_models; delete p; }
inline bool llama_model_has_encoder(const llama_model*) {return false;}
inline bool llama_model_has_decoder(const llama_model*) {return true;}
inline const char* llama_model_chat_template(const llama_model* p,const char*) {return p->tmpl;}
inline int llama_model_n_ctx_train(const llama_model*) {return 8192;}
inline const llama_vocab* llama_model_get_vocab(const llama_model* p) {return &p->vocab;}
inline int llama_chat_apply_template(const char* tmpl,const llama_chat_message*,size_t,bool,char* out,size_t cap) {if (!tmpl || std::strcmp(tmpl,"stub")) return -1; if(out && cap) out[0]='x'; return 1;}
inline int llama_tokenize(const llama_vocab*,const char*,int,llama_token* out,int cap,bool,bool) {if(cap<2) return -2; out[0]=100;out[1]=100; return 2;}
inline llama_context_params llama_context_default_params() { return {}; }
inline llama_context* llama_init_from_model(llama_model*,llama_context_params p) {return new llama_context{p.n_ctx};}
inline unsigned llama_n_ctx(const llama_context* c) {return c->limit;}
inline void llama_free(llama_context* c) {delete c;}
inline llama_batch llama_batch_get_one(llama_token* t,int n) {return {t,n};}
inline int llama_decode(llama_context* c,llama_batch b) {c->decoded+=b.n; return c->decoded>c->limit ? 1:0;}
inline llama_sampler_chain_params llama_sampler_chain_default_params(){return {};}
inline llama_sampler* llama_sampler_chain_init(llama_sampler_chain_params){return new llama_sampler;}
inline void llama_sampler_free(llama_sampler* p){for(auto c:p->children) llama_sampler_free(c);delete p;}
inline void llama_sampler_chain_add(llama_sampler* s,llama_sampler* c){s->children.push_back(c);s->seed=c->seed;}
inline llama_sampler* llama_sampler_init_top_k(int){return new llama_sampler;}
inline llama_sampler* llama_sampler_init_top_p(float,size_t){return new llama_sampler;}
inline llama_sampler* llama_sampler_init_temp(float){return new llama_sampler;}
inline llama_sampler* llama_sampler_init_dist(uint32_t seed){auto s=new llama_sampler;s->seed=seed;return s;}
// seed is the deterministic number of content tokens before EOG.
inline llama_token llama_sampler_sample(llama_sampler* s,llama_context* c,int){if(c->decoded!=static_cast<unsigned>(s->sampled+2)) std::abort();return static_cast<unsigned>(s->sampled++)>=s->seed?0:1;}
inline bool llama_vocab_is_eog(const llama_vocab*,llama_token t){return t==0;}
inline int llama_token_to_piece(const llama_vocab*,llama_token,char* out,int cap,int,bool){if(cap<1)return -1;out[0]='a';return 1;}
