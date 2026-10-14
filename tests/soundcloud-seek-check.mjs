import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const source=fs.readFileSync(process.argv[2],'utf8');
const template=source.match(/return """([\s\S]*?)""".replace/)[1];
const events=[];
let dragging=false,failMove=false;
class MouseEvent {constructor(type,options){this.type=type;Object.assign(this,options);}}
const wrapper={getBoundingClientRect:()=>({top:20,height:16}),dispatchEvent(event){
 events.push(event);dragging=true;
}};
const track={getBoundingClientRect:()=>({left:10,width:200})};
const context={location:{origin:'https://soundcloud.com'},window:{},MouseEvent,
 document:{querySelector:selector=>selector.includes('Background')?track:wrapper,
 dispatchEvent(event){events.push(event);if(event.type==='mouseup')dragging=false;
  if(event.type==='mousemove'&&failMove)throw new Error('fixture failure');}}};
for(const ratio of [0,.25,.75,1]){
 events.length=0;
 assert.equal(vm.runInNewContext(template.replaceAll('RATIO',String(ratio)),context).sent,true);
 assert.equal(dragging,false,'release always clears browser drag state');
 assert.deepEqual(events.map(event=>event.type),['mousedown','mousemove','mouseup']);
 for(const event of events)assert.equal(event.clientX,10+200*ratio,'all events target new position, never old handle');
 assert.equal(events.at(-1).buttons,0);
}
failMove=true;
assert.throws(()=>vm.runInNewContext(template.replaceAll('RATIO','.5'),context));
assert.equal(dragging,false,'release also sent when movement handler fails');
console.log('SoundCloud seek fixtures passed: one gesture, exact target, release on success/failure');
