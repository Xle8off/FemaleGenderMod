import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const source=fs.readFileSync(process.argv[2],'utf8');
const template=source.match(/return """([\s\S]*?)""".replace/)[1];
let level=100,media=[];
const events=new Map(),timers=[];
const player={setVolume(v){level=v;},unMute(){}};
const context={
 location:{origin:'https://music.youtube.com'},window:{},
 document:{querySelector:()=>player,querySelectorAll:()=>media,
  addEventListener(name,fn){if(events.has(name))throw new Error('duplicate listener');events.set(name,fn);}},
 setTimeout(fn){timers.push(fn);}
};
for(const ratio of [.35,.75,0,1]){
 assert.equal(vm.runInNewContext(template.replaceAll('RATIO',String(ratio)),context),true);
 assert.equal(level,Math.round(ratio*100));
 level=100;events.get('loadedmetadata')();assert.equal(level,Math.round(ratio*100));
 level=100;timers.pop()();assert.equal(level,Math.round(ratio*100));
}
const target={value:26,closest(){return true;}};
events.get('input')({target});
level=100;events.get('loadedmetadata')();assert.equal(level,26,'website change supersedes mod preference');
context.location.origin='https://accounts.google.com';
assert.equal(vm.runInNewContext(template.replaceAll('RATIO','.5'),context),false);
console.log('YouTube volume fixtures passed: player setting, track resets, bounded retry, web changes, origin guard');
