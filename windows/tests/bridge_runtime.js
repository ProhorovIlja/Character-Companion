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

function run({text, send=false, origin='https://character.ai', editorCount=1, buttonCount=1,
  frame=false, href, expectedPage, onFocus, onDispatch, onFindButtons, detachButton=false}) {
  href = href ?? origin + '/chat/abc';
  expectedPage = expectedPage ?? href;
  let clicked = 0;
  let dispatched = 0;
  class FakeTextArea {
    constructor() { this._value=''; this.disabled=false; this.isConnected=true; this.parentElement={parentElement:parent}; }
    get value() { return this._value; }
    set value(next) { this._value=next; }
    focus() { if (onFocus) onFocus(context,this); }
    getBoundingClientRect() { return {width:300,height:50}; }
    getAttribute(name) { return name === 'type' ? null : null; }
    closest(selector) { return selector==='form' ? parent : null; }
    dispatchEvent(event) { dispatched++; if (onDispatch) onDispatch(context,this,event); }
  }
  const buttons = Array.from({length:buttonCount},()=>({
    disabled:false, isConnected:!detachButton, getBoundingClientRect:()=>({width:25,height:25}),
    getAttribute:(k)=> k==='aria-label' ? 'Send' : null,
    click:()=> { clicked++; }
  }));
  const parent={
    querySelectorAll:()=>{ if (onFindButtons) onFindButtons(context,editors,buttons); return buttons; },
    contains:(el)=>el.isConnected && (buttons.includes(el) || editors.includes(el))
  };
  const editors = Array.from({length:editorCount},()=>new FakeTextArea());
  const doc={ querySelectorAll:()=>editors };
  const win={}; win.top=frame?{}:win;
  const context={window:win,location:{origin,href},document:doc,
    getComputedStyle:()=>({visibility:'visible',display:'block'}),
    HTMLTextAreaElement:FakeTextArea,
    InputEvent:class { constructor(type,opts) { this.type=type;this.options=opts;} },
    Event:class{ constructor(type,opts) {this.type=type;this.options=opts;} }
  };
  const script=template.replace('{{payload}}',JSON.stringify(text)).replace('{{sendArg}}',send?'true':'false').replace('{{pageArg}}',JSON.stringify(expectedPage));
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
{
  // A stale dictation must not edit/send to another chat on the same allowed origin.
  const x=run({text:'Do not send', send:true,
    href:'https://character.ai/chat/second', expectedPage:'https://character.ai/chat/first'});
  assert.equal(x.result.ok,false); assert.equal(x.result.reason,'page-changed');
  assert.equal(x.clicked,0); assert.equal(x.dispatched,0);
}
{
  const x=run({text:'Do not send', send:true,
    href:'https://character.ai/chat/first#different',expectedPage:'https://character.ai/chat/first'});
  assert.equal(x.result.ok,false); assert.equal(x.clicked,0);
}
{
  const x=run({text:'All good', send:true,
    href:'https://character.ai/chat/first', expectedPage:'https://character.ai/chat/first'});
  assert.equal(x.result.ok,true); assert.equal(x.clicked,1);
}
{
  // Focusing an editor may run a page-specific handler synchronously.
  const x=run({text:'not sent',send:true,onFocus:context=>{context.location.href='https://character.ai/chat/other';}});
  assert.equal(x.result.ok,false); assert.equal(x.clicked,0); assert.equal(x.dispatched,0);
}
{
  // React/website input handlers can navigate while the bridge is running.
  const x=run({text:'not sent',send:true,onDispatch:(context,el,event)=>{
    if(event.type==='input') context.location.href='https://character.ai/chat/other';
  }});
  assert.equal(x.result.ok,false); assert.equal(x.clicked,0);
}
{
  // The site may replace the composer in response to the input event.
  const x=run({text:'not sent',send:true,onDispatch:(context,el,event)=>{
    if(event.type==='input') el.isConnected=false;
  }});
  assert.equal(x.result.ok,false); assert.equal(x.clicked,0);
}
{
  // The page could change during DOM enumeration after inserting the text.
  const x=run({text:'not sent',send:true,onFindButtons:context=>{
    context.location.href='https://character.ai/chat/other';
  }});
  assert.equal(x.result.ok,false); assert.equal(x.clicked,0);
}
{
  // A detached Send button is never clicked, even if still enumerable.
  const x=run({text:'not sent',send:true,detachButton:true});
  assert.equal(x.result.ok,false); assert.equal(x.clicked,0);
}
console.log('ComposerBridge runtime: PASS 16 deterministic DOM scenarios');
