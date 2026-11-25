#include "chat_template_probe.h"
#include <iostream>
#include <stdexcept>
int main() {
    int checks = 0;
    auto verify = [&](bool ok) { if (!ok) throw std::runtime_error("probe check failed"); ++checks; };
    verify(mandy_chat_template_supported("stub"));
    for (const char* tmpl : {"unsupported", "drops-system", "drops-assistant", "reordered", "duplicate", "oversized", "embedded-nul"}) {
        // The old nonempty-output gate incorrectly accepted each malformed
        // but recognized template except the explicitly unsupported one.
        const llama_chat_message old[] = {{"system", "Identity"}, {"user", "Hello"}};
        if (std::string_view(tmpl) != "unsupported") verify(llama_chat_apply_template(tmpl, old, 2, true, nullptr, 0) > 0);
        verify(!mandy_chat_template_supported(tmpl));
    }
    verify(!mandy_chat_template_supported(nullptr));
    std::cout << "PASS " << checks << " template probe assertions (simulated formatter)\n";
}
