// Evaluate the actual JSON envelope constructed for CefSharp.EvaluateScriptAsync.
// Tests the adapter's expression syntax against the real ComposerBridge C# raw string.
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');
const root = path.join(__dirname, '../CharacterCompanion.Windows');
const win = fs.readFileSync(path.join(root,'MainWindow.xaml.cs'),'utf8');
assert(win.includes("BuildScript(text, send, expectedPage ?? browserPage).Trim().TrimEnd(';')"));
const bridge = fs.readFileSync(path.join(root,'Security/ComposerBridge.cs'),'utf8');
const match = bridge.match(/return \$\$"""\s*([\s\S]*?)\s*""";/);
assert(match);
const makeScript = (text, send, origin) => {
  const expression=match[1].replace('{{payload}}',JSON.stringify(text))
    .replace('{{sendArg}}',send?'true':'false')
    .replace('{{pageArg}}',JSON.stringify(origin + '/chat/abc')).trim().replace(/;+$/,'');
  const js='JSON.stringify('+expression+')';
  const context={ window:null, location:{origin,href:origin+'/chat/abc'}, document:null };
  const w={};w.top=w;context.window=w;
  return {js,context};
};
for (const origin of ['https://character.ai','https://character.ai.evil.net']) {
  const {js,context}=makeScript('Привет "мир"!',true,origin);
  // For untrusted origin, the bridge exits before touching the DOM.
  if (origin.includes('evil.net')) {
    const result=JSON.parse(vm.runInNewContext(js,context,{timeout:1000}));
    assert.strictEqual(result.ok,false);
    assert.strictEqual(result.reason,'untrusted-origin');
  } else {
    // Syntax must parse even if no browser DOM exists yet.
    new vm.Script(js);
  }
}
console.log('CefSharp script envelope: PASS 2 scenarios');
