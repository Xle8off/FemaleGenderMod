'use strict';
// Trusted main-process code only. Remote pages have no Node/Electron access.
const {app,BrowserWindow,session,components}=require('electron');
const http=require('node:http');const crypto=require('node:crypto');const fs=require('node:fs');
let win,server,frame=null,frameId=0,lastViewer=0,lastParent=Date.now(),token='',status='starting',error='',muted=true,busy=false;
let parentPid=0,localTest=false,captureFps=60;
 let cinemaState={ready:false},cinemaStateBusy=false,cinemaStateAt=0,localVolume=0,audioGain=0,viewWidth=1280,viewHeight=720;
function refreshCinemaState(){
 if(cinemaStateBusy||Date.now()-cinemaStateAt<750||!browserReady())return;
 try{if(!cinemaPath(new URL(win.webContents.getURL()).pathname))return;}catch{return;}
 cinemaStateAt=Date.now();cinemaStateBusy=true;
 win.webContents.executeJavaScript('window.springplusCinemaState?.()',false).then(s=>{if(!s||typeof s!=='object'){cinemaState={ready:false};return;}cinemaState={ready:s.ready===true,canControl:s.canControl===true,url:String(s.url||'').slice(0,2048),playing:s.playing===true,hasVideo:s.hasVideo===true,p2p:s.p2p!==false,share:s.share===true,status:String(s.status||'').slice(0,240)};}).catch(()=>{cinemaState={ready:false};}).finally(()=>{cinemaStateBusy=false;});
}
let videoStream=null,streamBusy=false,fullscreen=false,paintMs=0,sourceSize='',dropped=0,cinemaViewing=false,cinemaControls=false;
function cinemaPath(path){return /^\/(?:[a-z0-9-]+\/)*player$/.test(path);}
function notifyCinemaControls(value){if(cinemaControls===value)return;cinemaControls=value;if(browserReady())try{if(cinemaPath(new URL(win.webContents.getURL()).pathname))win.webContents.executeJavaScript('window.springplusCinemaControls?.('+value+')',false).catch(()=>{});}catch{}}
function notifyCinemaViewing(value){if(cinemaViewing===value)return;cinemaViewing=value;if(browserReady())try{if(cinemaPath(new URL(win.webContents.getURL()).pathname))win.webContents.executeJavaScript('window.springplusCinemaViewing?.('+value+')',false).catch(()=>{});}catch{}}
function applyAudio(){if(!browserReady())return;const level=localVolume*audioGain;win.webContents.setAudioMuted(level===0);win.webContents.executeJavaScript('window.springplusCinemaVolume?.('+level+')',false).catch(()=>{});}
function mediaNeeded(){return audioGain>0||Date.now()-lastViewer<=3000;}
function viewing(){const wasIdle=Date.now()-lastViewer>3000;lastViewer=Date.now();if(browserReady()){if(!win.webContents.isPainting())win.webContents.startPainting();if(wasIdle)win.webContents.invalidate();notifyCinemaViewing(mediaNeeded());applyAudio();}}
function sendFrame(){
 if(!videoStream||streamBusy||!frame)return;
 const target=videoStream;streamBusy=true;
 const header=Buffer.allocUnsafe(12);header.writeBigUInt64BE(BigInt(frameId));header.writeUInt32BE(frame.length,8);
 target.cork();target.write(header);target.write(frame,()=>{if(videoStream===target)streamBusy=false;});target.uncork();
}
app.commandLine.appendSwitch('autoplay-policy','document-user-activation-required');
app.commandLine.appendSwitch('force-device-scale-factor','1');
app.commandLine.appendSwitch('disable-background-networking');
app.commandLine.appendSwitch('disable-features','WebRtcAllowInputVolumeAdjustment');
function validUrl(value){try{const u=new URL(value);return !u.username&&!u.password&&!!u.hostname&&(u.protocol==='https:'&&(!u.port||u.port==='443')||localTest&&u.protocol==='http:'&&u.hostname==='127.0.0.1'&&u.pathname==='/player');}catch{return false;}}
function authorized(req){const given=Buffer.from(req.headers['x-springplus-token']||'');const expected=Buffer.from(token);return given.length===expected.length&&crypto.timingSafeEqual(given,expected);}
function reply(res,code,body,type='application/json'){res.writeHead(code,{'Content-Type':type,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(body);}
function fail(message){error=message;status='error';}
function browserReady(){return win&&!win.isDestroyed();}
function handle(req,res){
 if(!authorized(req)){reply(res,401,'{}');return;}
 lastParent=Date.now();
 if(req.method==='GET'&&req.url==='/status'){const gain=req.headers['x-springplus-audio-gain'];if(gain!==undefined){const n=Number(gain);if(!Number.isFinite(n)||n<0||n>1){reply(res,400,'gain');return;}if(audioGain!==n){audioGain=n;applyAudio();}}else if(req.headers['x-springplus-viewing']==='1'&&audioGain===0){audioGain=1;applyAudio();}if(req.headers['x-springplus-viewing']==='1')viewing();notifyCinemaViewing(mediaNeeded());notifyCinemaControls(req.headers['x-springplus-controls']==='1');refreshCinemaState();reply(res,200,JSON.stringify({status,error,cinema:cinemaState,frame:frameId,muted,volume:localVolume,audioGain,effectiveVolume:localVolume*audioGain,mediaNeeded:mediaNeeded(),painting:browserReady()&&win.webContents.isPainting(),width:viewWidth,height:viewHeight,fps:browserReady()?win.webContents.getFrameRate():captureFps,fullscreen,paintMs,sourceSize,dropped}));return;}
 if(req.method==='GET'&&req.url==='/frames'){
  if(videoStream){reply(res,409,'{}');return;}
  videoStream=res;streamBusy=false;req.socket.setNoDelay(true);
  res.writeHead(200,{'Content-Type':'application/x-springplus-frame-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.flushHeaders();
  res.on('close',()=>{if(videoStream===res){videoStream=null;streamBusy=false;}});viewing();if(browserReady())win.webContents.invalidate();sendFrame();return;
 }
 if(req.method==='GET'&&req.url==='/frame'){
  viewing();
  if(!frame||String(frameId)===req.headers['x-springplus-after']){reply(res,204,'');return;}
  res.setHeader('X-SpringPlus-Frame',String(frameId));reply(res,200,frame,'application/x-springplus-bgra');return;
 }
 if(req.method!=='POST'||req.url!=='/control'){reply(res,404,'{}');return;}
 let chunks=[],size=0;
 req.on('data',chunk=>{size+=chunk.length;if(size>8192){req.destroy();return;}chunks.push(chunk);});
 req.on('end',()=>{try{
  if(size>8192)throw Error('size');const data=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  if(data.action==='stop'){reply(res,200,'{}');setImmediate(()=>app.quit());return;}
  if(!browserReady()){reply(res,409,'{}');return;}
  switch(data.action){
   case 'navigate':{
    if(!validUrl(data.url))throw Error('url');const next=new URL(data.url),current=new URL(win.webContents.getURL());
    if(current.origin===next.origin&&cinemaPath(current.pathname)&&next.pathname===current.pathname&&next.hash)win.webContents.executeJavaScript('window.springplusCinemaRenew?.('+JSON.stringify(next.hash.slice(1))+')',false).catch(()=>{});
    else win.webContents.loadURL(data.url).catch(()=>fail('Не удалось загрузить страницу'));break;
   }
   case 'volume':if(typeof data.value!=='number'||!Number.isFinite(data.value)||data.value<0||data.value>1)throw Error('volume');localVolume=data.value;muted=localVolume===0;applyAudio();break;
   case 'mute':muted=data.value!==false;win.webContents.setAudioMuted(muted||audioGain===0);try{if(cinemaPath(new URL(win.webContents.getURL()).pathname))win.webContents.executeJavaScript('window.springplusCinemaMute?.('+muted+')',true).catch(()=>{});}catch{}break;
   case 'back':if(win.webContents.navigationHistory.canGoBack())win.webContents.navigationHistory.goBack();break;
   case 'reload':win.webContents.reload();break;
   case 'exitFullscreen':win.webContents.executeJavaScript('if(document.fullscreenElement)document.exitFullscreen()',true).catch(()=>{});break;
   case 'cinemaControls':notifyCinemaControls(data.value===true);break;
   case 'cinemaCommand':{
    if(!cinemaPath(new URL(win.webContents.getURL()).pathname))throw Error('player');
    const command=data.command;if(!command||!['navigate','back','reload','close','toggle','seek','p2p','share','upload'].includes(command.operation))throw Error('command');
    if(command.operation==='navigate'&&(typeof command.value!=='string'||command.value.length>2048))throw Error('url');
    const safe={operation:command.operation};if(['string','boolean','number'].includes(typeof command.value))safe.value=command.value;
    win.webContents.executeJavaScript('window.springplusCinemaCommand?.('+JSON.stringify(safe)+')',true).catch(()=>{});break;
   }
   case 'cinemaTicket':{
    if(!validUrl(data.url))throw Error('url');const next=new URL(data.url),current=new URL(win.webContents.getURL());
    if(current.origin!==next.origin||!cinemaPath(current.pathname)||next.pathname!==current.pathname||next.hash.length>2049)throw Error('origin');
    win.webContents.executeJavaScript('window.springplusCinemaRenew?.('+JSON.stringify(next.hash.slice(1))+')',false).catch(()=>{});break;
   }
   case 'input':{
    const event=data.event||{};const allowed=['mouseDown','mouseUp','mouseMove','mouseWheel','keyDown','keyUp','char'];if(!allowed.includes(event.type))throw Error('input');
    if(event.type.startsWith('mouse')){event.x=Math.max(0,Math.min(viewWidth-1,Math.floor(Number(event.x)||0)));event.y=Math.max(0,Math.min(viewHeight-1,Math.floor(Number(event.y)||0)));}
    if(event.type==='mouseWheel'){event.deltaX=Math.max(-1000,Math.min(1000,Number(event.deltaX)||0));event.deltaY=Math.max(-1000,Math.min(1000,Number(event.deltaY)||0));}
    if(event.type==='char'&&(typeof event.keyCode!=='string'||event.keyCode.length>8))throw Error('char');
    if(event.type==='char'&&cinemaPath(new URL(win.webContents.getURL()).pathname)){
     win.webContents.executeJavaScript('window.springplusCinemaText?.('+JSON.stringify(event.keyCode)+')',true).then(handled=>{if(handled!==true&&browserReady())win.webContents.sendInputEvent(event);}).catch(()=>{});break;
    }
    if(cinemaControls&&['mouseDown','mouseUp','mouseMove','keyDown'].includes(event.type)&&cinemaPath(new URL(win.webContents.getURL()).pathname))win.webContents.executeJavaScript('window.springplusCinemaGesture?.('+JSON.stringify({type:event.type,x:event.x,y:event.y})+')',true).catch(()=>{});
    win.webContents.focus();win.webContents.sendInputEvent(event);break;
   }
   default:throw Error('action');
  }
  reply(res,200,'{}');
 }catch{reply(res,400,'{}');}});
}
async function start(config){
 viewWidth=config.width??1280;viewHeight=config.height??720;if(!Number.isInteger(viewWidth)||!Number.isInteger(viewHeight)||viewWidth<1||viewWidth>2560||viewHeight<1||viewHeight>920||viewWidth*viewHeight>1800000)throw Error('viewport');
 localTest=config.localTest===true;localVolume=typeof config.volume==='number'&&Number.isFinite(config.volume)?Math.max(0,Math.min(1,config.volume)):0;muted=localVolume===0;
 if(process.platform!=='win32'||process.arch!=='x64')throw Error('Windows x64 required');
 if(typeof config.token!=='string'||!/^[a-f0-9]{64}$/.test(config.token)||!validUrl(config.url)||typeof config.profile!=='string')throw Error('Invalid startup config');
 token=config.token;parentPid=Number(config.parentPid)||0;
 app.setPath('userData',config.profile);
 server=http.createServer(handle);server.requestTimeout=5000;server.headersTimeout=5000;
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 fs.writeFileSync(config.readyPath,String(server.address().port),{encoding:'utf8',mode:0o600});
 await app.whenReady();
 const ses=session.defaultSession;
 // Only gesture-driven HTML fullscreen. Camera, mic, automatic fullscreen and other permissions stay denied.
 ses.setPermissionRequestHandler((contents,permission,callback)=>callback(contents===win?.webContents&&permission==='fullscreen'));
 ses.setPermissionCheckHandler((contents,permission)=>contents===win?.webContents&&permission==='fullscreen');
 ses.on('will-download',event=>event.preventDefault());
 status='components';
 // Component installation failure does not disable ordinary, unencrypted pages.
 try{if(components)await components.whenReady([components.WIDEVINE_CDM_ID]);}catch{error='Widevine недоступен; защищённое видео может не работать';}
 win=new BrowserWindow({width:viewWidth,height:viewHeight,useContentSize:true,frame:false,show:false,backgroundColor:'#000000',webPreferences:{offscreen:true,disableHtmlFullscreenWindowResize:true,backgroundThrottling:false,nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true,plugins:true}});
 captureFps=config.fps??60;if(![60,30,24].includes(captureFps))throw Error('fps');
 win.webContents.setFrameRate(captureFps);win.webContents.setAudioMuted(true);
 win.webContents.setWindowOpenHandler(({url})=>{if(validUrl(url))win.webContents.loadURL(url).catch(()=>{});return {action:'deny'};});
 win.webContents.on('will-navigate',(event,url)=>{if(!validUrl(url))event.preventDefault();});
 win.webContents.on('will-redirect',(event,url)=>{if(!validUrl(url))event.preventDefault();});
 win.webContents.on('render-process-gone',()=>fail('Процесс страницы завершился'));
 win.on('enter-html-full-screen',()=>{fullscreen=true;});
 win.on('leave-html-full-screen',()=>{fullscreen=false;});
 win.webContents.on('paint',(_event,_dirty,image)=>{
  if(busy||Date.now()-lastViewer>3000||image.isEmpty())return;
  if(videoStream&&streamBusy){dropped++;return;}
  const began=performance.now();busy=true;try{const size=image.getSize();sourceSize=size.width+'x'+size.height;const normalized=size.width===viewWidth&&size.height===viewHeight?image:image.resize({width:viewWidth,height:viewHeight,quality:'good'});const bitmap=normalized.toBitmap();if(bitmap.length===viewWidth*viewHeight*4){frame=bitmap;frameId++;sendFrame();}}finally{busy=false;paintMs=paintMs*.9+(performance.now()-began)*.1;}
 });
 win.webContents.on('did-finish-load',()=>{status='ready';const controls=cinemaControls;cinemaControls=null;notifyCinemaControls(controls===true);cinemaViewing=null;notifyCinemaViewing(mediaNeeded());applyAudio();});
 await win.loadURL(config.url);status='ready';
 setInterval(()=>{if(!browserReady())return;if(Date.now()-lastViewer>3000){notifyCinemaViewing(mediaNeeded());win.webContents.stopPainting();if(!mediaNeeded())win.webContents.setAudioMuted(true);}},1000).unref();
}
setInterval(()=>{let alive=true;if(parentPid>0)try{process.kill(parentPid,0);}catch{alive=false;}if(!alive||Date.now()-lastParent>60000)app.quit();},5000).unref();
let flushing=false;
app.on('window-all-closed',()=>app.quit());
app.on('before-quit',event=>{
 if(win&&!flushing){event.preventDefault();flushing=true;session.defaultSession.cookies.flushStore().catch(()=>{}).finally(()=>app.quit());return;}
 videoStream?.destroy();server?.close();frame=null;
});
// Windows GUI executables cannot reliably inherit console stdin/stdout.
// One-use private handoff file; only its random path is on the command line.
const configArg=process.argv.find(value=>value.startsWith('--springplus-config='));
try{
 if(!configArg)throw Error('Missing config');const file=configArg.slice(20);
 let config;try{if(fs.statSync(file).size>8192)throw Error('Config size');config=JSON.parse(fs.readFileSync(file,'utf8'));}finally{fs.unlinkSync(file);}
 if(typeof config.readyPath!=='string')throw Error('Missing ready path');
 start(config).catch(()=>{fail('Не удалось запустить браузер');setTimeout(()=>app.quit(),3000);});
}catch{app.quit();}
