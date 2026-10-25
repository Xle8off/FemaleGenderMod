package com.wildfire.gui;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local display preferences only. Never changes server delivery or permissions. */
public final class ChatAllFilters {
    public static final List<String> CHANNELS=List.of("global","local","discord","staff","system","spy");
    private static final ExecutorService DISK=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SpringPlus chat filters");t.setDaemon(true);return t;});
    private final Path file;
    private final Set<String> hidden=new HashSet<>();
    public ChatAllFilters(Path file){
        this.file=file;
        try{if(Files.isRegularFile(file)&&Files.size(file)<=1024)for(String line:Files.readAllLines(file))if(CHANNELS.contains(line)||line.equals("@hideTabs")||line.equals("@vanillaChat")||line.equals("@noSmoothShift")||line.equals("@noSmoothScroll")||line.matches("@(anim|exit):(OFF|FADE|LEFT|UP|RIGHT|DOWN|LOWER_LEFT|UPPER_RIGHT)")||line.matches("@curve:(EASE_OUT|LINEAR|SMOOTH)")||line.matches("@speed:(0|1|2)"))hidden.add(line);}catch(Exception ignored){}
    }
    public boolean visible(String channel){return !hidden.contains(channel);}
    public boolean showTabs(){return !hidden.contains("@hideTabs");}
    public boolean vanillaChat(){return hidden.contains("@vanillaChat");}
    public ChatMotion.Appearance appearance(){
        for(var style:ChatMotion.Appearance.values())if(hidden.contains("@anim:"+style.name()))return style;
        return ChatMotion.Appearance.LEFT;
    }
    public void cycleAppearance(){
        var styles=ChatMotion.Appearance.values();var next=styles[(appearance().ordinal()+1)%styles.length];
        hidden.removeIf(s->s.startsWith("@anim:"));hidden.add("@anim:"+next.name());save();
    }
    public ChatMotion.Appearance disappearance(){
        for(var style:ChatMotion.Appearance.values())if(hidden.contains("@exit:"+style.name()))return style;
        return ChatMotion.Appearance.FADE;
    }
    public String disappearanceLabel(){return switch(disappearance()){
        case OFF->"Без анимации";case FADE->"Затухание";case LEFT->"Влево";case RIGHT->"Вправо";
        case UP->"Вниз";case DOWN->"Вверх";case LOWER_LEFT->"Влево вниз";case UPPER_RIGHT->"Вправо вверх";};}
    public void cycleDisappearance(){
        var styles=ChatMotion.Appearance.values();var next=styles[(disappearance().ordinal()+1)%styles.length];
        hidden.removeIf(s->s.startsWith("@exit:"));hidden.add("@exit:"+next.name());save();
    }
    public ChatMotion.Curve curve(){
        for(var curve:ChatMotion.Curve.values())if(hidden.contains("@curve:"+curve.name()))return curve;
        return ChatMotion.Curve.EASE_OUT;
    }
    public void cycleCurve(){
        var curves=ChatMotion.Curve.values();var next=curves[(curve().ordinal()+1)%curves.length];
        hidden.removeIf(s->s.startsWith("@curve:"));hidden.add("@curve:"+next.name());save();
    }
    public int speed(){for(int i=0;i<3;i++)if(hidden.contains("@speed:"+i))return i;return 1;}
    public String speedLabel(){return switch(speed()){case 0->"Быстро";case 2->"Медленно";default->"Обычно";};}
    public long durationNanos(){return switch(speed()){case 0->120_000_000L;case 2->360_000_000L;default->220_000_000L;};}
    public void cycleSpeed(){int next=(speed()+1)%3;hidden.removeIf(s->s.startsWith("@speed:"));hidden.add("@speed:"+next);save();}
    public boolean smoothShift(){return !hidden.contains("@noSmoothShift");}
    public boolean smoothScroll(){return !hidden.contains("@noSmoothScroll");}
    public void toggleSmoothShift(){toggleSetting("@noSmoothShift");}
    public void toggleSmoothScroll(){toggleSetting("@noSmoothScroll");}
    public void toggleVanillaChat(){toggleSetting("@vanillaChat");}
    public void toggleTabs(){toggleSetting("@hideTabs");}
    public void toggle(String channel){
        if(!CHANNELS.contains(channel))return;
        toggleSetting(channel);
    }
    private void toggleSetting(String channel){
        if(!hidden.remove(channel))hidden.add(channel);
        save();
    }
    private void save(){
        String snapshot=String.join("\n",new TreeSet<>(hidden));
        DISK.execute(()->{try{
            Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"chat-filters-",".tmp");
            try{Files.writeString(temp,snapshot);replace(temp,file);}finally{Files.deleteIfExists(temp);}
        }catch(Exception ignored){}});
    }
    private static void replace(Path temp,Path file)throws Exception{
        // Windows readers/antivirus can briefly deny replacement; keep the latest queued save.
        for(int attempt=0;;attempt++){
            try{
                try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
                return;
            }catch(FileSystemException busy){
                if(attempt>=5)throw busy;
                Thread.sleep(25L*(attempt+1));
            }
        }
    }
}
