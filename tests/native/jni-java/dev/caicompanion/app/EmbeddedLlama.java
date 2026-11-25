package dev.caicompanion.app;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
public class EmbeddedLlama {
    private native long nativeLoadV2(byte[] path, Sink sink) throws IOException;
    private native long[] nativeGenerateV3(long handle, byte[][] roles, byte[][] contents, int context, int output, int seed, int threads, Sink sink) throws IOException;
    private native void nativeClose(long handle);
    public static class Sink {
        StringBuilder text = new StringBuilder();
        public boolean isCancelled() { return false; }
        public void onBytes(byte[] bytes) { text.append(new String(bytes, StandardCharsets.UTF_8)); }
    }
    static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        System.loadLibrary("mandy_llama");
        var engine = new EmbeddedLlama();
        int checks = 0;
        for (String invalid : new String[]{"unsupported", "missing", "drops-system", "drops-assistant", "reordered", "duplicate", "oversized", "embedded-nul"}) {
            try {
                long bad = engine.nativeLoadV2(utf8(invalid + ".gguf"), new Sink());
                engine.nativeClose(bad);
                throw new AssertionError("Unexpected accepted template: " + invalid);
            } catch (IOException e) {
                check(e.getMessage().equals("Unsupported local model chat template"));
                checks++;
            }
            long good = engine.nativeLoadV2(utf8("synthetic.gguf"), new Sink());
            try {
                check(good != 0); checks++;
                Sink sink = new Sink();
                long[] stats = engine.nativeGenerateV3(good, new byte[][]{utf8("system"), utf8("user"), utf8("assistant"), utf8("user")}, new byte[][]{utf8("Identity"),utf8("Hello"),utf8("Hi"),utf8("How are you?")},512,4,4,4,sink);
                check(stats.length == 8 && stats[0] == 2 && stats[1] == 4 && stats[2] == 4 && sink.text.toString().equals("aaaa")); checks++;
            } finally { engine.nativeClose(good); }
        }
        System.out.println("PASS " + checks + " JNI template admission/recovery assertions (Java harness, simulated backend)");
    }
}
