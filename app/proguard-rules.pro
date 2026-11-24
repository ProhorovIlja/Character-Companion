# Keep Android WebView's JavaScript interfaces disabled; no @JavascriptInterface methods.
# No custom serialization reflection or privileged bridges.

# Native method names and JNI callback signatures must survive R8.
-keep class dev.caicompanion.app.EmbeddedLlama { *; }
-keep class dev.caicompanion.app.EmbeddedTextSink { *; }
