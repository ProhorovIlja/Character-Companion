// Execute the real JavaScript payload extracted from ComposerBridge.BuildScript.
// This is a DOM simulation, not an integration test against the live Character.AI site.
'use strict';
const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const path = require('path');
const source = fs.readFileSync(path.join(__dirname, '../CharacterCompanion.Windows/Security/ComposerBridge.cs'), 'utf8');
const match = source.match(/return \$\$"""\s*([\s\S]*?)\s*""";/);
assert(match, 'Could not extract bridge JS template from actual C# source');
const template = match[1];
assert(template.includes('{{payload}}') && template.includes('{{sendArg}}'));

function run({text, send=false, origin='https://character.ai', editorCount=1, buttonCount=1, frame=false}) {
  let clicked = 0;
  let dispatched = 0;
  class FakeTextArea {
    constructor() { this._value=''; this.disabled=false; this.parentElement={parentElement:parent}; }
    get value() { return this._value; }
    set value(next) { this._value=next; }
    focus() {}
    getBoundingClientRect() { return {width:300,height:50}; }
    getAttribute(name) { return name === 'type' ? null : null; }
    closest(selector) { return selector==='form' ? parent : null; }
    dispatchEvent() { dispatched++; }
  }
  const buttons = Array.from({length:buttonCount},()=>({
    disabled:false, getBoundingClientRect:()=>({width:25,height:25}),
    getAttribute:(k)=> k==='aria-label' ? 'Send' : null,
    click:()=> { clicked++; }
  }));
  const parent={querySelectorAll:()=>buttons};
  const editors = Array.from({length:editorCount},()=>new FakeTextArea());
  const doc={ querySelectorAll:()=>editors };
  const win={}; win.top=frame?{}:win;
  const context={window:win,location:{origin},document:doc,
    getComputedStyle:()=>({visibility:'visible',display:'block'}),
    HTMLTextAreaElement:FakeTextArea,
    InputEvent:class { constructor(type,opts) { this.type=type;this.options=opts;} },
    Event:class{ constructor(type,opts) {this.type=type;this.options=opts;} }
  };
  const script=template.replace('{{payload}}',JSON.stringify(text)).replace('{{sendArg}}',send?'true':'false');
  const result=vm.runInNewContext(script,context,{timeout:1000});
  return {result,clicked,dispatched,editors};
}
{
  const payload='Привет, "Character"!\n<script>alert(1)</script> 💬';
  const x=run({text:payload});
  assert.equal(x.result.ok,true);assert.equal(x.result.sent,false);
  assert.equal(x.editors[0].value,payload);
  assert.equal(x.clicked,0);assert.equal(x.dispatched,2);
}
{
  const x=run({text:'Hello',send:true});
  assert.equal(x.result.ok,true);assert.equal(x.result.sent,true);
  assert.equal(x.clicked,1);
}
{
  const x=run({text:'Hello',send:true,editorCount:2});
  assert.equal(x.result.ok,false);assert.equal(x.clicked,0);
  assert.equal(x.dispatched,0);
}
{
  const x=run({text:'Hello',send:true,buttonCount:2});
  assert.equal(x.result.ok,true);assert.equal(x.result.sent,false);
  assert.equal(x.clicked,0);
}
{
  const x=run({text:'Hello',send:true,buttonCount:0});
  assert.equal(x.result.ok,true);assert.equal(x.result.sent,false);
  assert.equal(x.clicked,0);
}
{
  const x=run({text:'Hello',send:true,origin:'https://character.ai.evil.example'});
  assert.equal(x.result.ok,false);assert.equal(x.result.reason,'untrusted-origin');
  assert.equal(x.clicked,0);assert.equal(x.dispatched,0);
}
{
  const x=run({text:'Hello',send:true,frame:true});
  assert.equal(x.result.ok,false);assert.equal(x.result.reason,'untrusted-origin');
  assert.equal(x.clicked,0);
}
{
  const x=run({text:'Hello',send:true,origin:'https://chat.character.ai'});
  assert.equal(x.result.ok,true);assert.equal(x.clicked,1);
}
console.log('ComposerBridge runtime: PASS 8 deterministic DOM scenarios');
