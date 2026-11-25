#pragma once
#include "llama.h"
#include <array>
#include <string_view>

// A recognized lightweight template may still discard a role's content.
// Check one bounded multi-turn prompt before retaining the model.
inline bool mandy_chat_template_supported(const char * tmpl) {
    if (!tmpl) return false;
    constexpr const char * markers[] = {
        "MandyProbeSystem_7A91", "MandyProbeUser_8B02",
        "MandyProbeAssistant_9C13", "MandyProbeLatest_0D24"
    };
    const llama_chat_message messages[] = {
        {"system", markers[0]}, {"user", markers[1]},
        {"assistant", markers[2]}, {"user", markers[3]}
    };
    std::array<char, 4096> buffer{};
    const int length = llama_chat_apply_template(tmpl, messages, 4, true,
        buffer.data(), static_cast<int>(buffer.size()));
    if (length <= 0 || static_cast<size_t>(length) >= buffer.size()) return false;
    const std::string_view rendered(buffer.data(), static_cast<size_t>(length));
    if (rendered.find('\0') != std::string_view::npos) return false;
    size_t cursor = 0;
    for (const char * marker : markers) {
        const std::string_view value(marker);
        const size_t found = rendered.find(value);
        if (found == std::string_view::npos || found < cursor ||
                rendered.find(value, found + value.size()) != std::string_view::npos) return false;
        cursor = found + value.size();
    }
    return true;
}
