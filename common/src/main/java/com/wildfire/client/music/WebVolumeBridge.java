package com.wildfire.client.music;

/** Drive the website volume input itself, not a cosmetic container or raw audio element. */
final class WebVolumeBridge {
    static String set(double ratio, boolean yandex) {
        if (!Double.isFinite(ratio) || ratio < 0 || ratio > 1) throw new IllegalArgumentException();
        String origin = yandex ? "https://music.yandex.ru" : "https://open.spotify.com";
        String selector = yandex
            ? YandexMediaBridge.VOLUME_SELECTOR
            : "[data-testid=volume-bar] input[type=range]";
        String track = yandex
            ? YandexMediaBridge.VOLUME_SELECTOR
            : "[data-testid=volume-bar] [data-testid=progress-bar], [data-testid=volume-bar] [role=slider]";
        return """
            (() => {
              if(location.origin!==ORIGIN) return null;
              const ratio=RATIO;
              const input=document.querySelector(SELECTOR);
              if(input instanceof HTMLInputElement && !input.disabled) {
                const min=Number(input.min || 0), max=Number(input.max || 100);
                const step=Number(input.step || 1);
                const raw=min+(max-min)*ratio;
                const value=step>0 ? min+Math.round((raw-min)/step)*step : raw;
                // Use the prototype setter: assigning input.value directly updates React's
                // value tracker before the event, so its onChange may ignore the change.
                Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,String(Math.max(min,Math.min(max,value))));
                input.dispatchEvent(new Event('input',{bubbles:true}));
                input.dispatchEvent(new Event('change',{bubbles:true}));
                // React's controlled widget may ignore synthetic input/change events.
                // Invoke only this element's own UI handler, never account/player internals.
                const propsKey=Object.keys(input).find(key=>key.startsWith('__reactProps$'));
                const handler=propsKey&&input[propsKey]?.onChange;
                if(typeof handler==='function') {
                  handler({target:input,currentTarget:input,nativeEvent:new Event('input',{bubbles:true}),
                    preventDefault(){},stopPropagation(){}});
                  return {input:true};
                }
              }
              const candidates=[...document.querySelectorAll(TRACK)];
              if(input) candidates.push(input);
              const element=candidates.find(e => {const r=e.getBoundingClientRect(); return r.width>8 && r.height>0;});
              if(!element) return input ? {input:true} : null;
              const r=element.getBoundingClientRect();
              return {x:r.left+Math.max(1,Math.min(r.width-1,r.width*ratio)),y:r.top+r.height/2};
            })()
            """.replace("ORIGIN", "'" + origin + "'").replace("RATIO", Double.toString(ratio))
               .replace("SELECTOR", "'" + selector + "'").replace("TRACK", "'" + track + "'");
    }
}
