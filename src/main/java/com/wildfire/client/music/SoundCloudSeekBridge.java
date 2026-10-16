package com.wildfire.client.music;

/** Use the site's interactive timeline; SoundCloud need not expose an HTML audio element. */
final class SoundCloudSeekBridge {
    private SoundCloudSeekBridge() {}
    static String set(double ratio) {
        if (!Double.isFinite(ratio) || ratio < 0 || ratio > 1) throw new IllegalArgumentException();
        return """
            (() => {
              if(location.origin!=='https://soundcloud.com')return null;
              const track=document.querySelector('.playbackTimeline__progressBackground');
              const wrapper=document.querySelector('.playbackTimeline__progressWrapper');
              if(!track||!wrapper)return null;
              const r=track.getBoundingClientRect(),w=wrapper.getBoundingClientRect();
              if(r.width<=0||w.height<=0)return null;
              const options={bubbles:true,view:window,clientX:r.left+r.width*RATIO,
                clientY:w.top+w.height/2,button:0,buttons:1};
              // A complete gesture in one evaluation; do not first seek to the old handle.
              wrapper.dispatchEvent(new MouseEvent('mousedown',options));
              try {
                document.dispatchEvent(new MouseEvent('mousemove',options));
              } finally {
                document.dispatchEvent(new MouseEvent('mouseup',{...options,buttons:0}));
              }
              return {sent:true};
            })()
            """.replace("RATIO", Double.toString(ratio));
    }
}
