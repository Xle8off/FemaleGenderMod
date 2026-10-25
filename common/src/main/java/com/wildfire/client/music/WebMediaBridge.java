package com.wildfire.client.music;

/** Standard browser media bridge, without accessing credentials or internal service APIs. */
final class WebMediaBridge {
    static final String INSTALL = """
        (() => {
          if(location.origin!=='https://music.yandex.ru' || window.__spMedia) return;
          const handlers = Object.create(null);
          window.__spMedia = handlers;
          const session = navigator.mediaSession;
          if(session) {
            if(typeof session.setPositionState==='function') {
              const setPosition=session.setPositionState.bind(session);
              session.setPositionState=state=>{
                setPosition(state);
                window.__spPosition=state && Number.isFinite(state.duration) ? {
                  duration:state.duration,position:state.position||0,rate:state.playbackRate||1,at:performance.now()
                } : null;
              };
            }
            const original = session.setActionHandler.bind(session);
            session.setActionHandler = (name, handler) => {
              original(name, handler);
              if(handler) handlers[name] = handler; else delete handlers[name];
            };
          }
        })();
        """;
    static final String READ = """
        (() => {
          if(location.origin!=='https://music.yandex.ru') return {};
          const media = [...document.querySelectorAll('audio,video')].find(e => !e.paused)
                     || document.querySelector('audio,video');
          const session = navigator.mediaSession, meta = session?.metadata;
          return {title:meta?.title || '',artist:meta?.artist || '',
            playing:media ? !media.paused : session?.playbackState==='playing',
            volume:media ? media.volume : null, volumeMin:0,volumeMax:1,
            positionMs:media && Number.isFinite(media.currentTime) ? media.currentTime*1000 : 0,
            durationMs:media && Number.isFinite(media.duration) ? media.duration*1000 : 0};
        })()
        """;
    static String action(String action, boolean playing) {
        String name = switch(action) { case "next" -> "nexttrack"; case "previous" -> "previoustrack";
            case "playpause" -> playing ? "pause" : "play"; default -> throw new IllegalArgumentException(); };
        return "(() => { if(location.origin!=='https://music.yandex.ru') return false;"
            + "const h=window.__spMedia?.['" + name + "']; if(h){h({action:'" + name + "'});return true;}"
            + (action.equals("playpause") ? "const m=document.querySelector('audio,video');if(m){if(m.paused)m.play().catch(()=>{});else m.pause();return true;}" : "")
            + "return false; })()";
    }
    static String volume(double ratio) {
        if (!Double.isFinite(ratio) || ratio < 0 || ratio > 1) throw new IllegalArgumentException();
        return "(() => {if(location.origin!=='https://music.yandex.ru')return false;"
            + "const media=[...document.querySelectorAll('audio,video')];if(!media.length)return false;"
            + "for(const m of media){m.volume=" + ratio + ";m.muted=false;}"
            + "const slider=[...document.querySelectorAll('input[type=range]')].find(e=>/volume|громк/i.test(e.getAttribute('aria-label')||e.name||''));"
            + "if(slider){const min=Number(slider.min||0),max=Number(slider.max||1);"
            + "const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
            + "setter.call(slider,String(min+(max-min)*" + ratio + "));"
            + "slider.dispatchEvent(new Event('input',{bubbles:true}));slider.dispatchEvent(new Event('change',{bubbles:true}));}"
            + "return true;})()";
    }
    static String seek(double ratio) {
        if (!Double.isFinite(ratio) || ratio < 0 || ratio > 1) throw new IllegalArgumentException();
        return "(() => {if(location.origin!=='https://music.yandex.ru')return false;"
            + "const m=[...document.querySelectorAll('audio,video')].find(e=>!e.paused)||document.querySelector('audio,video');"
            + "const duration=m&&Number.isFinite(m.duration)?m.duration:window.__spPosition?.duration;"
            + "if(!Number.isFinite(duration)||duration<=0)return false;"
            + "const time=duration*" + ratio + ";const h=window.__spMedia?.seekto;"
            + "if(h)h({action:'seekto',seekTime:time,fastSeek:false});else if(m)m.currentTime=time;else return false;return true;})()";
    }
    static String install(MusicWebsite site) { return onSite(INSTALL, site); }
    static String read(MusicWebsite site) {
        if (site == MusicWebsite.YANDEX) return YandexMediaBridge.READ;
        String script = READ;
        if (site == MusicWebsite.SOUNDCLOUD) script = script.replace("meta?.title || ''",
            "meta?.title || document.querySelector('.playbackSoundBadge__titleLink')?.getAttribute('title') || ''")
            .replace("meta?.artist || ''", "meta?.artist || document.querySelector('.playbackSoundBadge__lightLink')?.textContent?.trim() || ''");
        if (site == MusicWebsite.SOUNDCLOUD)
            script = script.replace("volume:media ? media.volume : null", "volume:(" + SoundCloudVolumeBridge.READ + ") ?? (media ? media.volume : null)");
        if (site == MusicWebsite.SOUNDCLOUD) {
            String clock = """
                const clockMs=(selector)=>{
                  const box=document.querySelector(selector);
                  const text=box?.querySelector('[aria-hidden="true"]')?.textContent || box?.textContent || '';
                  const match=text.match(/(?:\\d+:)?\\d+:\\d{2}/);
                  return match ? match[0].split(':').reduce((total,part)=>total*60+Number(part),0)*1000 : 0;
                };
                """;
            script = script.replace("const session = navigator.mediaSession", clock + "const session = navigator.mediaSession")
                .replace("positionMs:media && Number.isFinite(media.currentTime) ? media.currentTime*1000 : 0",
                    "positionMs:clockMs('.playbackTimeline__timePassed') || (media && Number.isFinite(media.currentTime) ? media.currentTime*1000 : 0)")
                .replace("durationMs:media && Number.isFinite(media.duration) ? media.duration*1000 : 0",
                    "durationMs:clockMs('.playbackTimeline__duration') || (media && Number.isFinite(media.duration) ? media.duration*1000 : 0)");
        }
        return onSite(script, site);
    }
    static String volume(double ratio, MusicWebsite site) { return onSite(volume(ratio), site); }
    static String seek(double ratio, MusicWebsite site) { return onSite(seek(ratio), site); }
    static String action(String action, boolean playing, MusicWebsite site) {
        if (site == MusicWebsite.YANDEX) return YandexMediaBridge.action(action);
        String script = action(action, playing);
        String selector = site == MusicWebsite.SOUNDCLOUD
            ? switch(action) { case "next" -> ".skipControl__next"; case "previous" -> ".skipControl__previous"; default -> ".playControl"; }
            : site == MusicWebsite.YOUTUBE
            ? switch(action) { case "next" -> "ytmusic-player-bar .next-button"; case "previous" -> "ytmusic-player-bar .previous-button"; default -> "ytmusic-player-bar #play-pause-button"; } : "";
        if (!selector.isEmpty()) script = script.replace("return false; })()",
            "const button=document.querySelector('" + selector + "');if(button&&!button.disabled){button.click();return true;}return false; })()");
        return onSite(script, site);
    }
    private static String onSite(String script, MusicWebsite site) {
        return script.replace("https://music.yandex.ru", site.url.substring(0, site.url.length()-1));
    }
}
