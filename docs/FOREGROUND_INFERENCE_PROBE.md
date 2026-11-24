# Foreground bilingual inference probe

This manually invoked Python reference utility measures two synthetic conversation requests (English then Russian) against an explicitly started trusted local llama.cpp server. It uses the existing authenticated numeric loopback streaming provider. No chat database, user identity, model text logging, server launch, model download, telemetry, scheduled task or monitoring. It prints a small JSON report only if both requests complete. Enter the private API key interactively; it is not a command-line argument or output field. Server logging/cache privacy remains the responsibility of the selected trusted server.

From the repository root, after manually starting a local server as described in WINDOWS_LOCAL_CHAT_BASELINE.md:

    py -3 -m shared.inference_probe --port 8080 --model mandy-local

On Linux use python3 instead. The existing provider imposes bounded SSE framing and a 120-second socket inactivity timeout, not a total deadline. The probe bounds output at 16000 UTF-16 units, rejects blank/invalid/unfinished responses and closes the stream. No generated response content is printed. Report fields: language (requested, not detected), first_text_ms (first nonempty visible fragment), complete_ms (normal completion including client I/O), output_utf16_units (not tokens). These timings include local HTTP overhead; no token/s estimate, memory measurement, language accuracy, quality score or device-performance claim. One short request per language, fixed order, uncontrolled warm/cold cache and output length; use upstream llama-bench and matched runtime configurations for controlled throughput measurement. Do not compare EN/RU response lengths as if identical token workloads.

Research consulted:
- https://github.com/ggml-org/llama.cpp/tree/master/tools/llama-bench — separate prompt-processing and generation throughput.
- https://github.com/ggml-org/llama.cpp/tree/master/tools/server/bench/speed-bench — server-side timings and comparison.
- https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md — CUDA and platform options.
- https://github.com/SciSharp/LLamaSharp/blob/master/docs/QuickStart.md — managed native binding candidate; backend/ABI matching required.
- https://developer.nvidia.com/management-library-nvml — optional explicit GPU resource measurements; not integrated here.
- https://learn.microsoft.com/dotnet/desktop/wpf/advanced/optimizing-performance-controls — WPF container virtualization/recycling.
- https://developer.android.com/topic/performance/baselineprofiles/overview — real release build startup/interaction optimization; measure on target hardware.

These sources inform the existing integration policy; none of their code or model weights was imported. No new dependency. Android Baseline Profiles and WPF virtualization are future implementation candidates, not completed changes. Native Windows C# compilation remains unverified because SDK is unavailable. No real model or hardware timings obtained.

Validation: six new unittest methods passed with deterministic provider/clock doubles, covering measurements, bounded invalid output, stream cleanup, all-or-nothing report and private key handling. The full existing shared reference test suite plus new tests passed 42/42 on the Linux host. Exact new Python source blobs match the tested files. Run manually: python -m unittest discover -s tests -p test_inference_probe.py. No Windows or Android executable built.
