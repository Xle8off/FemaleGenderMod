import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire('C:/Users/Myakk/WebstormProjects/my/profileskinsys/package.json');
const { PNG } = require('pngjs');
const [source, unicodeFile, ...roots] = process.argv.slice(2);
const translated = {};
for (const file of ['ru.json','ru-derived.json']) {
  const data=JSON.parse(fs.readFileSync(path.join(path.dirname(unicodeFile),file),'utf8'));
  Object.assign(translated,data.annotations?.annotations || data.annotationsDerived.annotations);
}
const groups = ['Smileys & Emotion','People & Body','Animals & Nature','Food & Drink','Travel & Places','Activities','Objects','Symbols','Flags'];
let group = ''; const entries = []; const variants = [];
for (const line of fs.readFileSync(unicodeFile, 'utf8').split(/\r?\n/)) {
  if (line.startsWith('# group: ')) group = line.slice(9);
  const m = line.match(/^([0-9A-F ]+)\s*;\s*(fully-qualified|minimally-qualified|unqualified)\s*#\s*\S+\s+E[\d.]+\s+(.+)$/);
  if (!m) continue;
  const points = m[1].trim().split(/ +/).map(p => parseInt(p,16));
  const unicode = String.fromCodePoint(...points);
  if (m[2] !== 'fully-qualified') { variants.push({unicode,name:m[3]}); continue; }
  const candidates = [points.map(p=>p.toString(16)).join('-'), points.filter(p=>p!==0xfe0f).map(p=>p.toString(16)).join('-')];
  const asset = candidates.map(id=>path.join(source,id+'.png')).find(f=>fs.existsSync(f));
  if (!asset) throw new Error('Missing Twemoji: '+candidates[0]);
  entries.push({id:candidates[0],unicode,label:m[3],group:groups.indexOf(group),asset});
}
if (entries.length < 3900 || entries.some(e=>e.group<0)) throw new Error('Incomplete Unicode 17 catalog');
const aliases = variants.map(v=>({unicode:v.unicode,index:entries.findIndex(e=>e.label===v.name)})).filter(v=>v.index>=0);
const providers = [], images = [];
for (let start=0; start<entries.length; start+=256) {
  const page=start/256; const png=new PNG({width:1152,height:1152}); const rows=[];
  for(let row=0;row<16;row++) {
    let chars='';
    for(let col=0;col<16;col++) {
      const i=start+row*16+col; const e=entries[i];
      if (!e) { chars+='\u0000'; continue; }
      const points=[...e.unicode];
      e.glyph=points.length===1 ? e.unicode : String.fromCodePoint(0xe000+i);
      e.page=page; e.cell=row*16+col;
      chars+=e.glyph;
      const tile=PNG.sync.read(fs.readFileSync(e.asset));
      if(tile.width!==72 || tile.height!==72) throw new Error('Unexpected dimensions');
      // Preserve original resolution for scaled GUI icons.
      PNG.bitblt(tile,png,0,0,72,72,col*72,row*72);
    }
    rows.push(chars);
  }
  images.push(PNG.sync.write(png));
  providers.push({type:'bitmap',file:`appearance_sync:gui/emoji/atlas-${page}.png`,ascent:8,height:9,chars:rows});
}
for(const root of roots) {
  const dir=path.join(root,'assets/appearance_sync');
  fs.mkdirSync(path.join(dir,'textures/gui/emoji'),{recursive:true});
  images.forEach((data,i)=>{
    const asset=path.join(dir,`textures/gui/emoji/atlas-${i}.png`);
    fs.writeFileSync(asset,data);
    fs.writeFileSync(asset+'.mcmeta',JSON.stringify({texture:{blur:true,clamp:true}}));
  });
  fs.writeFileSync(path.join(dir,'emoji-catalog.json'),JSON.stringify({version:17,entries:entries.map(({asset,...e})=>{
    const ru=translated[e.unicode] || translated[e.unicode.replaceAll('\ufe0f','')];
    return {...e,label:ru?.tts?.[0] || e.label,search:e.label+' '+(ru?.default || []).join(' ')};
  }),aliases}));
  const old=JSON.parse(fs.readFileSync(path.join(dir,'font/emoji.json'),'utf8'));
  // Retain the twelve original glyphs and aliases, then add the full Unicode set.
  old.providers=old.providers.filter(p=>!p.file.includes('/atlas-')).concat(providers);
  fs.writeFileSync(path.join(dir,'font/emoji.json'),JSON.stringify(old));
}
console.log(`${entries.length} Unicode 17 emoji, ${aliases.length} alternate sequences, ${images.length} atlases, ${images.reduce((s,p)=>s+p.length,0)} PNG bytes`);
