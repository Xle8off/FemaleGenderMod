import fs from 'node:fs';
import assert from 'node:assert/strict';
const source = fs.readFileSync(process.argv[2], 'utf8');
const template = source.match(/return """([\s\S]*?)"""/)[1];
const script = ratio => template.replaceAll('ORIGIN', "'https://open.spotify.com'")
  .replaceAll('RATIO', String(ratio))
  .replaceAll('SELECTOR', "'[data-testid=volume-bar] input[type=range]'")
  .replaceAll('TRACK', "'[data-testid=volume-bar] [data-testid=progress-bar], [data-testid=volume-bar] [role=slider]'");
class Input {
  constructor() { this._value = '1'; this.tracker = '1'; this.state = '1'; this.min = '0'; this.max = '1'; this.step = '.01'; this.events = []; }
  get value() { return this._value; }
  set value(v) { this._value = v; }
  dispatchEvent(event) {
    this.events.push(event.type);
    if (this.tracker !== this._value) { this.state = this._value; this.tracker = this._value; }
  }
  getBoundingClientRect() { return {left:10,top:20,width:100,height:8}; }
}
globalThis.HTMLInputElement = Input;
globalThis.location = {origin:'https://open.spotify.com'};
let input = new Input();
Object.defineProperty(input, 'value', { get() { return this._value; }, set(v) { this._value = v; this.tracker = v; } });
globalThis.document = {querySelector:() => input,querySelectorAll:() => [input]};
input.value = '.35'; input.dispatchEvent(new Event('input'));
assert.equal(input.state, '1', 'ordinary assignment is ignored by controlled input');
for(const ratio of [0,.35,.75,1]) {
  const point = eval(script(ratio));
  assert.ok(Math.abs(Number(input.state) - ratio) < 1e-9, 'native setter reaches website state');
  assert.ok(point.x >= 11 && point.x <= 109);
  assert.ok(input.events.includes('change'));
}
let handled = 0;
input.__reactProps$fixture = {onChange(event) { handled++; input.state = event.target.value; }};
assert.deepEqual(eval(script(.42)), {input:true}, 'React handler avoids mouse round trips');
assert.equal(input.state, '0.42');
assert.equal(handled, 1);
location.origin = 'https://accounts.spotify.com';
assert.equal(eval(script(.5)), null, 'login page is not manipulated');
location.origin = 'https://open.spotify.com';
document.querySelector = () => null;
document.querySelectorAll = () => [];
assert.equal(eval(script(.5)), null, 'absent control is not reported successful');
console.log('Spotify volume fixture passed: controlled input, mute, intermediate values, safe endpoints, origin guard');
