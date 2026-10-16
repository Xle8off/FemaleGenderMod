import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const source = fs.readFileSync(process.argv[2], 'utf8');
const template = source.match(/return """([\s\S]*?)""".replace/)[1];
const script = ratio => template.replaceAll('RATIO', String(ratio));
const fill = {style:{height:'20%'}};
const style = {cssText:'display:none',setProperty(key,value){this.cssText=key+':'+value;}};
const events = [];
const wrapper = {style,dispatchEvent(event){events.push(event.type);}};
const track = {getBoundingClientRect(){
  assert.notEqual(style.cssText, 'display:none', 'hidden wrapper is laid out only during command');
  return {left:10,width:4,bottom:110,height:100};
}};
const document = {
  querySelector(selector) {
    return selector === '.volume__sliderBackground' ? track
      : selector === '.volume__sliderWrapper' ? wrapper
      : selector === '.volume__sliderProgress' ? fill : null;
  },
  dispatchEvent(event) {
    events.push(event.type);
    if(event.type==='mousemove')fill.style.height=((110-event.clientY)/100*100)+'%';
  }
};
class MouseEvent {constructor(type, options){this.type=type;Object.assign(this,options);}}
const context = {location:{origin:'https://soundcloud.com'},document,window:{},MouseEvent};
for(const ratio of [0,.35,.75,1]){
  const result=vm.runInNewContext(script(ratio),context);
  assert.equal(result.sent,true,'drag submitted once without waiting for repaint');
  assert.equal(style.cssText,'display:none','original visibility restored');
  assert.ok(Math.abs(parseFloat(fill.style.height)/100-ratio)<.001);
}
assert.ok(events.includes('mousedown')&&events.includes('mousemove')&&events.includes('mouseup'));
context.location.origin='https://accounts.soundcloud.com';
assert.equal(vm.runInNewContext(script(.5),context),null,'auth origin untouched');
console.log('SoundCloud hidden-volume fixtures passed: event drag, intermediate values, restored CSS, origin guard');
