import com.wildfire.gui.ChatMotion;
import com.wildfire.gui.ChatAllFilters;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class ChatMotionCheck {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        // ActiveTextCollector uses left/right/top/bottom, NOT x/y/width/height.
        int left=8,top=120,width=320,bottomEdge=280;
        var clip=new net.minecraft.client.gui.ActiveTextCollector.Parameters(new org.joml.Matrix3x2f())
            .withOpacity(1).withScissor(left,left+width,top,bottomEdge).scissor();
        check(clip.left()==left&&clip.right()==left+width&&clip.top()==top&&clip.bottom()==bottomEdge,
            "open chat text clip preserves full history bounds");
        check(clip.containsPoint(left+10,bottomEdge-12),"latest text line visible on opening");
        long duration=220_000_000L,now=1_000_000_000L;
        for(var style:ChatMotion.Appearance.values()){
            var start=ChatMotion.entry(style,0,duration);
            var end=ChatMotion.entry(style,duration,duration);
            check(end.opacity()==1&&end.x()==0&&end.y()==0,"entry settles: "+style);
            check(ChatMotion.entry(style,-1,duration).opacity()>=0,"negative clock clamps");
            float prior=-1;
            for(long t=0;t<=duration;t+=1_000_000L){
                var frame=ChatMotion.entry(style,t,duration);
                check(frame.opacity()>=prior&&frame.opacity()<=1,"monotonic opacity");prior=frame.opacity();
            }
            if(style==ChatMotion.Appearance.OFF)check(start.opacity()==1,"disabled appearance is immediate");
        }
        check(ChatMotion.entry(ChatMotion.Appearance.LEFT,0,duration).x()==-24,"left entry");
        check(ChatMotion.entry(ChatMotion.Appearance.UP,0,duration).y()==12,"up entry");
        check(ChatMotion.entry(ChatMotion.Appearance.RIGHT,0,duration).x()==24,"right entry");
        check(ChatMotion.entry(ChatMotion.Appearance.DOWN,0,duration).y()==-12,"top entry");
        for(var style:ChatMotion.Appearance.values()){
            var visible=ChatMotion.exit(style,1);
            check(visible.opacity()==1&&visible.x()==0&&visible.y()==0,"exit starts at rest");
            var gone=ChatMotion.exit(style,0);
            check(gone.opacity()==(style==ChatMotion.Appearance.OFF?1:0),"exit finishes or disabled");
            for(var curve:ChatMotion.Curve.values()){
                var finalFrame=ChatMotion.entry(style,duration,duration,curve);
                check(finalFrame.opacity()==1&&finalFrame.x()==0&&finalFrame.y()==0,"all curves settle");
            }
        }
        check((ChatMotion.color(0xFFFFFFFF,.5f)>>>24)==128,"media opacity");
        check(ChatMotion.lifetime(9000,10000,duration)==1,"HUD remains visible before fade");
        check(ChatMotion.lifetime(9890,10000,duration)==.5f,"HUD fades smoothly before expiry");
        check(ChatMotion.lifetime(10000,10000,duration)==0,"HUD invisible at expiry");
        check(ChatMotion.edge(120,14,120,280)==1,"fully visible first line remains opaque");
        check(ChatMotion.edge(113,14,120,280)==.5f,"partially clipped line fades");
        check(ChatMotion.edge(100,14,120,280)==0,"outside line invisible");
        Object a=new Object(),b=new Object();
        var bottom=new ChatMotion.Viewport();
        bottom.update(List.of(a),new int[]{14},0,100,now,duration,true,true,false);
        var inserted=bottom.update(List.of(a,b),new int[]{14,74},0,174,now+16_000_000L,duration,true,true,false);
        check(inserted.target()==0&&inserted.insertion()>0&&inserted.insertion()<74,"new sticker smoothly displaces old rows");
        for(int i=2;i<80;i++)inserted=bottom.update(List.of(a,b),new int[]{14,74},0,174,now+i*16_000_000L,duration,true,true,false);
        check(inserted.insertion()==0,"insertion finishes");
        var reading=new ChatMotion.Viewport();
        reading.update(List.of(a),new int[]{14},30,100,now,duration,true,true,false);
        var anchored=reading.update(List.of(a,b),new int[]{14,74},30,174,now+16_000_000L,duration,true,true,false);
        check(anchored.target()==104&&anchored.scroll()==104&&anchored.insertion()==0,"reading old history retains same message position");
        var scroll=new ChatMotion.Viewport();
        scroll.update(List.of(a),new int[]{14},0,100,now,duration,false,true,false);
        var moving=scroll.update(List.of(a),new int[]{14},84,100,now+16_000_000L,duration,false,true,false);
        check(moving.scroll()>0&&moving.scroll()<84,"wheel interpolates");
        moving=scroll.update(List.of(a),new int[]{14},84,100,now+32_000_000L,duration,false,true,true);
        check(moving.scroll()==84,"scrollbar drag is direct");
        moving=scroll.update(List.of(a),new int[]{14},0,100,now+48_000_000L,duration,false,false,false);
        check(moving.scroll()==0,"smooth wheel can be disabled");
        var sixty=new ChatMotion.Viewport();var high=new ChatMotion.Viewport();
        sixty.update(List.of(a),new int[]{14},0,100,now,duration,false,true,false);
        high.update(List.of(a),new int[]{14},0,100,now,duration,false,true,false);
        ChatMotion.Layout s=null,h=null;
        for(int i=1;i<=12;i++)s=sixty.update(List.of(a),new int[]{14},100,100,now+i*16_000_000L,duration,false,true,false);
        for(int i=1;i<=24;i++)h=high.update(List.of(a),new int[]{14},100,100,now+i*8_000_000L,duration,false,true,false);
        check(Math.abs(s.scroll()-h.scroll())<.001,"real-time smoothing is independent of FPS");
        Path directory=Files.createTempDirectory("springplus-chat-motion-"),file=directory.resolve("filters.txt");
        try{
            check(new ChatAllFilters(file).appearance()==ChatMotion.Appearance.LEFT,"new preferences appear left to right");
            Files.writeString(file,"@anim:LEFT\n@speed:2\n@noSmoothShift\n@noSmoothScroll\n@hideTabs\nglobal\n");
            var preferences=new ChatAllFilters(file);
            check(preferences.appearance()==ChatMotion.Appearance.LEFT&&preferences.durationNanos()==360_000_000L,"appearance/speed load");
            check(!preferences.smoothShift()&&!preferences.smoothScroll()&&!preferences.showTabs()&&!preferences.visible("global"),"preferences coexist with filters");
            preferences.cycleAppearance();preferences.cycleSpeed();preferences.toggleSmoothShift();preferences.toggleSmoothScroll();
            preferences.cycleDisappearance();preferences.cycleCurve();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            boolean saved=false;
            while(System.nanoTime()<deadline){
                var reloaded=new ChatAllFilters(file);
                if(reloaded.appearance()==ChatMotion.Appearance.UP&&reloaded.speed()==0&&reloaded.smoothScroll()&&reloaded.smoothShift()
                    &&reloaded.disappearance()==ChatMotion.Appearance.LEFT&&reloaded.curve()==ChatMotion.Curve.LINEAR){saved=true;break;}
                Thread.sleep(10);
            }
            check(saved,"animation preferences persist; stored="+Files.readString(file));
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(directory);}
        System.out.println("Chat motion: entry variants, timing, media alpha, history anchoring, smooth wheel, direct drag, FPS independence and saved settings passed");
    }
}
