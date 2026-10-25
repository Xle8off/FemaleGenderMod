package com.wildfire.client.music;

/** SoundCloud's visible volume widget, not private account or player APIs. */
final class SoundCloudVolumeBridge {
    private SoundCloudVolumeBridge() {}
    static final String HOVER = """
        (() => {
          if(location.origin!=='https://soundcloud.com')return null;
          const button=document.querySelector('.volume__button');if(!button)return null;
          const r=button.getBoundingClientRect();return r.width>0&&r.height>0?{x:r.left+r.width/2,y:r.top+r.height/2}:null;
        })()
        """;
    static final String READ = """
        (() => {
          if(location.origin!=='https://soundcloud.com')return null;
          const control=document.querySelector('.volume [role="slider"],.volume__sliderWrapper[aria-valuenow],.volume[aria-valuenow],.volume input[type="range"]');
          if(control){
            const raw=Number(control.value ?? control.getAttribute('aria-valuenow'));
            const min=Number(control.min || control.getAttribute('aria-valuemin') || 0);
            const max=Number(control.max || control.getAttribute('aria-valuemax') || 1);
            if(Number.isFinite(raw)&&max>min)return Math.max(0,Math.min(1,(raw-min)/(max-min)));
          }
          const fill=document.querySelector('.volume__sliderProgress');
          if(fill?.style.height?.endsWith('%'))return Math.max(0,Math.min(1,parseFloat(fill.style.height)/100));
          const bar=document.querySelector('.volume__sliderBackground');
          if(fill&&bar){const total=bar.getBoundingClientRect().height;if(total>0)return Math.max(0,Math.min(1,fill.getBoundingClientRect().height/total));}
          return null;
        })()
        """;
    static String set(double ratio) {
        if(!Double.isFinite(ratio)||ratio<0||ratio>1)throw new IllegalArgumentException();
        return """
            (() => {
              if(location.origin!=='https://soundcloud.com')return null;
              const input=document.querySelector('.volume input[type="range"]');
              if(input){
                const min=Number(input.min||0),max=Number(input.max||1);
                Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,String(min+(max-min)*RATIO));
                input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));
                return {done:true};
              }
              const track=document.querySelector('.volume__sliderBackground');
              const wrapper=document.querySelector('.volume__sliderWrapper');
              if(track&&wrapper){
                const previous=wrapper.style.cssText;
                try {
                  wrapper.style.setProperty('display','block','important');
                  wrapper.style.setProperty('visibility','visible','important');
                  const r=track.getBoundingClientRect();
                  if(r.width>0&&r.height>0){
                    const x=r.left+r.width/2,y=r.bottom-r.height*RATIO;
                    const handle=document.querySelector('.volume__sliderHandle'),h=handle?.getBoundingClientRect();
                    const startY=h&&h.height>0?h.top+h.height/2:r.bottom-r.height/2;
                    const options={bubbles:true,view:window,clientX:x,clientY:y,button:0,buttons:1};
                    wrapper.dispatchEvent(new MouseEvent('mousedown',{...options,clientY:startY}));
                    document.dispatchEvent(new MouseEvent('mousemove',options));
                    document.dispatchEvent(new MouseEvent('mouseup',{...options,buttons:0}));
                    // Rendering may lag behind the event. Do not submit a second drag
                    // merely because the progress fill has not repainted synchronously.
                    return {sent:true};
                  }
                } finally {wrapper.style.cssText=previous;}
              }
              if(track){const r=track.getBoundingClientRect();if(r.width>0&&r.height>0){
                const handle=document.querySelector('.volume__sliderHandle'),h=handle?.getBoundingClientRect();
                return {x:r.left+r.width/2,y:r.bottom-r.height*RATIO,
                  startX:r.left+r.width/2,startY:h&&h.height>0?h.top+h.height/2:r.bottom-r.height/2};
              }}
              const button=document.querySelector('.volume__button');
              if(button){const r=button.getBoundingClientRect();if(r.width>0&&r.height>0)
                return {open:true,x:r.left+r.width/2,y:r.top+r.height/2};}
              return null;
            })()
            """.replace("RATIO", Double.toString(ratio));
    }
}
