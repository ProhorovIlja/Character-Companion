# Mandy AI — local Windows text baseline (reference CLI)

This is a manually invoked Python 3.11+ standard-library client, not the final native WPF application, not a Windows executable, and not a bundled model. It reuses the existing immutable SQLite history and relationship context. One workspace per newly created directory, one character/chat, explicit send/retry/history commands. The client uses numeric IPv4 loopback HTTP only, with no proxy/redirect/cloud fallback. The CLI requires a locally entered bearer key (32–128 ASCII base64url-style characters); the lower-level provider retains its existing optional unauthenticated mode for compatibility. Keys are not written to workspace config or logs. The client cannot prove local process identity or guarantee that a separately launched server is configured securely. Use only a trusted local server executable, local model and private Windows account; do not send secrets to an unknown listener.

## Manual Windows validation

Use the repository root as working directory and Python 3.11+ (prefer the installed supported version). Select an existing private parent directory on the 1 TB NVMe volume, not a hardcoded drive. Prepare a UTF-8 identity text file (up to 8000 UTF-16 units). Run:

    py -3 -m shared.local_chat_cli init "<new workspace directory>" --identity "<identity file>"

In a separate terminal, explicitly start a trusted llama.cpp CUDA server with an existing trusted GGUF. The pinned upstream README documents the following flags. Use a privately created key file with a random base64url-style key, not the public fixture key. Keep the file outside repositories and sync folders, with appropriate account-only access.

    & "<llama-server.exe>" --model "<model.gguf>" --alias mandy-local --host 127.0.0.1 --port 8080 --api-key-file "<private key file>" --parallel 1 --ctx-size 4096 --gpu-layers auto --threads 6

The exact CUDA release, driver, model/architecture, server switches, VRAM budget and context are provisional; inspect the chosen build's help and local logs. CPU fallback requires an explicitly chosen compatible CPU build and --gpu-layers 0. No server is downloaded or launched by this client. Do not expose the server to LAN/WAN. Server may log or cache data according to its configuration; independently verify its privacy settings. Stop the server manually (Ctrl+C) after the session. No task scheduler, service or autostart.

After the server is ready, use:

    py -3 -m shared.local_chat_cli send "<workspace directory>"
    py -3 -m shared.local_chat_cli retry "<workspace directory>"
    py -3 -m shared.local_chat_cli history "<workspace directory>"

Each send reads one line from the terminal and requests a local response. The client displays the complete committed reply, not partial streaming output. An interrupted or invalid response leaves the original user message in SQLite; retry reuses that exact ID/text instead of creating a duplicate user message. A new send is refused while the latest user message is pending. No discard/recovery editor yet, no concurrent client processes supported, no cloud sync, no automatic relationship revision or voice. History displays the newest 50 messages in chronological order. Identity and SQLite data are plaintext at rest; do not use with sensitive personal information until local encryption and recovery are integrated. Init requires a new directory; an interrupted init may leave an incomplete directory requiring manual inspection. Missing history is refused, not silently recreated. Config and file checks assume a private cooperating filesystem and are not race-proof. The HTTP timeout is a socket inactivity timeout, not an overall deadline. The client has the existing 12000 UTF-16 context selection limit and 1024 response-token limit; no automatic context-fitting retry or model-specific reasoning control.

## Verification and source references

8 new Python unittest methods passed using real temporary SQLite workspaces and deterministic authenticated IPv4 HTTP/SSE fixture; 28 existing reference tests also passed (36 total). Tests exercise bilingual history/context, auth header, refusal and pending retry, invalid keys, missing DB, refusal to overwrite, duplicate config keys and terminal control escaping. This test ran on Linux, Python 3.13.5; no real model inference, Windows runtime, CUDA device or WPF build was available. No actual user data or secrets, no APK/EXE, no new external dependency.

Run the new reference tests manually from repo root with python -m unittest discover -s tests -p test_local_chat_cli.py. Upstream documentation reviewed: https://github.com/ggml-org/llama.cpp/blob/dbaf582af41941ebbfc4384fb9fa1b40dff7a510/tools/server/README.md ; https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md . No external source code copied. The selected architecture is an interim reference for actual model validation, not the final Windows embedded inference implementation.
