using System.Text.Json;

namespace CharacterCompanion.Windows.Security;

/// <summary>
/// Explicit user-requested DOM insertion. Fails closed when the website has more than one
/// possible composer or no identifiable send button. Never reads cookies or chat content.
/// </summary>
public static class ComposerBridge
{
    public static string BuildScript(string text, bool send, string expectedPage)
    {
        // JSON encoding prevents script injection even for quotes, HTML or newlines.
        var payload = JsonSerializer.Serialize(text);
        var sendArg = send ? "true" : "false";
        var pageArg = JsonSerializer.Serialize(expectedPage);
        return $$"""
        (() => {
          'use strict';
          const text = {{payload}};
          const send = {{sendArg}};
          const expectedPage = {{pageArg}};
          if (window.top !== window || !/^https:\/\/([a-z0-9-]+\.)*character\.ai(?::443)?$/i.test(location.origin)) {
            return {ok:false, reason:'untrusted-origin'};
          }
          // CEF may navigate during recognition or before EvaluateScriptAsync runs.
          // Validate the exact conversation page inside the same JS task that edits it.
          if (location.href !== expectedPage) {
            return {ok:false, reason:'page-changed'};
          }
          const visible = el => {
            const r = el.getBoundingClientRect();
            const s = getComputedStyle(el);
            return r.width > 3 && r.height > 3 && s.visibility !== 'hidden' && s.display !== 'none' && !el.disabled;
          };
          const editors = [...document.querySelectorAll('textarea, [contenteditable="true"][role="textbox"], [contenteditable="true"][data-placeholder]')]
            .filter(visible).filter(el => el.getAttribute('type') !== 'password');
          const unique = [...new Set(editors)];
          if (unique.length !== 1) return {ok:false, reason:'composer-not-unique'};
          const el = unique[0];
          // The site's focus listener may synchronously navigate or replace its editor.
          const stillSafe = () => location.href === expectedPage && el.isConnected;
          if (!stillSafe()) return {ok:false,reason:'composer-detached'};
          el.focus();
          if (!stillSafe()) return {ok:false,reason:'page-or-composer-changed'};
          if ('value' in el) {
            const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')?.set;
            if (!setter) return {ok:false,reason:'no-setter'};
            setter.call(el, text);
          } else {
            el.textContent = text;
          }
          el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));
          el.dispatchEvent(new Event('change',{bubbles:true}));
          // An input/change listener can synchronously rerender the whole composer,
          // or navigate to a different chat even though JS runs on one thread.
          if (!stillSafe()) return {ok:false,reason:'page-or-composer-changed'};
          if (!send) return {ok:true, sent:false};
          // Only use an explicitly labeled send control near the single known composer.
          const parent = el.closest('form') || el.parentElement?.parentElement;
          if (!parent) return {ok:true,sent:false,reason:'no-composer-container'};
          const buttons = [...parent.querySelectorAll('button')].filter(visible).filter(b => {
            const label = [b.getAttribute('aria-label'),b.getAttribute('title'),b.getAttribute('data-testid')]
              .filter(Boolean).join(' ').toLowerCase();
            return /(^|[\s_-])(send|отправить)([\s_-]|$)/.test(label);
          });
          if (buttons.length !== 1) return {ok:true,sent:false,reason:'send-button-not-unique'};
          // DOM queries and visibility getters can invoke website code. Check the
          // actual page and attached elements immediately before invoking Send.
          if (!stillSafe() || !parent.contains(el) || !buttons[0].isConnected ||
              !parent.contains(buttons[0])) {
            return {ok:false,sent:false,reason:'page-or-composer-changed'};
          }
          buttons[0].click();
          // Click was invoked; website acceptance cannot be verified from here.
          return {ok:true,sent:true,reason:'click-invoked-unconfirmed'};
        })();
        """;
    }

    public static (bool Inserted, bool ClickAttempted, string Reason) ParseResult(string? json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json ?? "null");
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object) return (false,false,"invalid-result");
            var ok = root.TryGetProperty("ok", out var a) && a.ValueKind == JsonValueKind.True;
            var sent = root.TryGetProperty("sent", out var b) && b.ValueKind == JsonValueKind.True;
            var reason = root.TryGetProperty("reason", out var c) ? c.GetString() ?? "" : "";
            return (ok,sent,reason);
        }
        catch (JsonException) { return (false,false,"invalid-json"); }
    }
}
