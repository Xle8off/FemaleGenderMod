package com.wildfire.client.music;

/** Public media-session state and accessible website controls only. */
final class YandexMediaBridge {
    static final String TIMELINE_SELECTOR = "input[data-test-id=TIMECODE_SLIDER],[data-test-id=TIMECODE_SLIDER] input,input[class*=VibePlayerbarMeta_slider],input[class*=PlayerBarDesktopWithBackgroundProgressBar_slider]";
    static final String VOLUME_SELECTOR = "input[type=range][aria-label*=volume i],input[type=range][aria-label*=громк i],[class*=Volume] input[type=range],[class*=volume] input[type=range],[role=slider][aria-label*=volume i],[role=slider][aria-label*=громк i],[class*=Volume] [role=slider],[class*=volume] [role=slider]";
    static final String READ = """
        (() => {
          if(location.origin!=='https://music.yandex.ru')return {};
          const session=navigator.mediaSession,meta=session?.metadata;
          const media=[...document.querySelectorAll('audio,video')].find(e=>!e.paused)||document.querySelector('audio,video');
          const slider=document.querySelector('VOLUME_SELECTOR');
          const min=Number(slider?.min||slider?.getAttribute('aria-valuemin')||0);
          const max=Number(slider?.max||slider?.getAttribute('aria-valuemax')||100);
          const raw=slider ? slider.value ?? slider.getAttribute('aria-valuenow') : null;
          const volume=raw!==null && raw!=='' && Number.isFinite(Number(raw)) && max>min
            ? Math.max(0,Math.min(1,(Number(raw)-min)/(max-min))) : media?.volume ?? null;
          const clock=selector=>{
            const text=document.querySelector(selector)?.textContent||'';
            const match=text.match(/(?:\\d+:)?\\d+:\\d{2}/);
            return match?match[0].split(':').reduce((n,p)=>n*60+Number(p),0)*1000:0;
          };
          const playing=media?!media.paused:session?.playbackState==='playing';
          const state=window.__spPosition;
          const timeline=document.querySelector('TIMELINE_SELECTOR');
          const times=(timeline?.getAttribute('aria-valuetext')||document.querySelector('[data-test-id=VIBE_PLAYERBAR_TIMECODE],[class*=VibePlayerbarMeta_timecodeOverlay]')?.textContent||'').match(/(?:\\d+:)?\\d+:\\d{2}/g)||[];
          const milliseconds=text=>text?text.split(':').reduce((n,p)=>n*60+Number(p),0)*1000:0;
          const domDuration=milliseconds(times[1])||clock('[data-test-id=TIMECODE_TIME_END]')||(timeline&&Number(timeline.max)>0?Number(timeline.max)*1000:0);
          const domPosition=times.length?milliseconds(times[0]):clock('[data-test-id=TIMECODE_TIME_START]')||(timeline&&Number.isFinite(Number(timeline.value))?Number(timeline.value)*1000:0);
          const duration=media&&Number.isFinite(media.duration)&&media.duration>0?media.duration*1000
            : domDuration||clock('[class*=ProgressBar_duration],[class*=PlayerBar_duration],.player-progress__duration')||((state?.duration||0)*1000);
          const position=media&&Number.isFinite(media.currentTime)?media.currentTime*1000
            : timeline || times.length ? domPosition : clock('[class*=ProgressBar_played],[class*=ProgressBar_position],[class*=PlayerBar_position],.player-progress__position')
              || (state?Math.min(state.duration,state.position+(playing?(performance.now()-state.at)/1000*state.rate:0))*1000:0);
          return {title:meta?.title||'',artist:meta?.artist||'',playing,volume,volumeMin:0,volumeMax:1,
            positionMs:Math.max(0,position),durationMs:Math.max(0,duration)};
        })()
        """.replace("VOLUME_SELECTOR", VOLUME_SELECTOR).replace("TIMELINE_SELECTOR", TIMELINE_SELECTOR);
    static String action(String action) {
        String selector = switch(action) {
            case "next" -> "[data-test-id=NEXT_TRACK_BUTTON],[data-test-id=MOBILE_NEXT_TRACK_BUTTON],button[aria-label*=Следующая],button[aria-label*=Next i]";
            case "previous" -> "[data-test-id=PREVIOUS_TRACK_BUTTON],[data-test-id=MOBILE_PREVIOUS_TRACK_BUTTON],button[aria-label*=Предыдущая],button[aria-label*=Previous i]";
            case "playpause" -> "button[class*=VibePlayerControls_playButton],button[aria-label*=Воспроизведение],button[aria-label*=Пауза],button[aria-label*=Pause i],button[aria-label*=Play i]";
            default -> throw new IllegalArgumentException();
        };
        return "(() => {if(location.origin!=='https://music.yandex.ru')return false;"
            + "const root=document.querySelector('[data-test-id=PLAYERBAR_DESKTOP],[data-test-id=VIBE_PLAYERBAR],[class*=PlayerBarDesktop_root],section[class*=VibePlayerBar_root]');"
            + "if(!root)return false;const button=[...root.querySelectorAll('" + selector + "')].find(b=>!b.disabled&&b.getAttribute('aria-disabled')!=='true');"
            + "if(!button)return false;button.click();return true;})()";
    }
    static String seek(double ratio) {
        if (!Double.isFinite(ratio) || ratio < 0 || ratio > 1) throw new IllegalArgumentException();
        return "(() => {if(location.origin!=='https://music.yandex.ru')return null;"
            + "const e=document.querySelector('" + TIMELINE_SELECTOR + "');if(!e||e.disabled)return null;"
            + "const r=e.getBoundingClientRect();if(r.width<=8||r.height<=0)return null;"
            + "const min=Number(e.min||0),max=Number(e.max||0);if(!(max>min))return null;"
            + "const current=Math.max(0,Math.min(1,(Number(e.value)-min)/(max-min)));"
            + "return {startX:r.left+Math.max(1,Math.min(r.width-1,r.width*current)),startY:r.top+r.height/2,"
            + "x:r.left+Math.max(1,Math.min(r.width-1,r.width*" + ratio + ")),y:r.top+r.height/2};})()";
    }
}
