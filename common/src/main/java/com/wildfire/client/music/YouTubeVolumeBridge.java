package com.wildfire.client.music;

/** Update the site's player setting, and restore it when YouTube replaces its media. */
final class YouTubeVolumeBridge {
    private YouTubeVolumeBridge() {}
    static String set(double ratio) {
        if (!Double.isFinite(ratio) || ratio < 0 || ratio > 1) throw new IllegalArgumentException();
        return """
            (() => {
              if(location.origin!=='https://music.youtube.com')return false;
              const state=window.__spYouTubeVolume ||= {value:null,installed:false};
              state.value=Math.round(RATIO*100);
              const apply=()=>{
                const player=document.querySelector('#movie_player');
                if(typeof player?.setVolume==='function'){
                  player.setVolume(state.value);
                  if(state.value>0&&typeof player.unMute==='function')player.unMute();
                  return true;
                }
                const media=[...document.querySelectorAll('audio,video')];
                for(const element of media){element.volume=state.value/100;element.muted=false;}
                return media.length>0;
              };
              state.apply=apply;
              if(!state.installed){
                state.installed=true;
                document.addEventListener('loadedmetadata',()=>{
                  state.apply();
                  const value=state.value;
                  setTimeout(()=>{if(state.value===value)state.apply();},150);
                },true);
                // A deliberate change in the website must supersede the mod's last value.
                const adopt=event=>{
                  const element=event.target;
                  if(!element?.closest?.('ytmusic-player-bar')||
                    !element.closest('#volume-slider,#expand-volume-slider,[aria-label*="volume" i],[aria-label*="громк" i]'))return;
                  const value=Number(element.value??event.detail?.value);
                  if(Number.isFinite(value)&&value>=0&&value<=100)state.value=Math.round(value);
                };
                document.addEventListener('input',adopt,true);
                document.addEventListener('change',adopt,true);
                document.addEventListener('value-changed',adopt,true);
              }
              return apply();
            })()
            """.replace("RATIO", Double.toString(ratio));
    }
}
