package dev.aeltorx.inflow.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class ModerationFavoritesScreen extends FixedModerationScreen {
    private final Screen parent;private final List<ModerationFavorites.Action> chosen=new ArrayList<>(ModerationFavorites.favorites());private int page;private String status="До 8 избранных действий · по 7 на странице";
    public ModerationFavoritesScreen(Screen parent){super(Component.literal("Избранные действия"));this.parent=parent;}
    @Override public boolean isPauseScreen(){return false;}
    private void rebuild(){clearWidgets();initMenu();}
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(ModerationButtons.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void initMenu(){int w=Math.min(400,width-24),x=(width-w)/2,top=42,visible=Math.max(1,(height-115)/24);var actions=ModerationFavorites.Action.values();int pages=(actions.length+visible-1)/visible;page=Math.min(page,pages-1);
        for(int i=page*visible;i<Math.min(actions.length,(page+1)*visible);i++){var a=actions[i];int row=top+(i%visible)*24;int index=chosen.indexOf(a);button((index>=0?"★ "+(index+1)+" · ":"☆ ")+a.label,x,row,w-82,()->{if(chosen.contains(a))chosen.remove(a);else if(chosen.size()<8)chosen.add(a);else status="Можно выбрать максимум 8 действий";rebuild();});button("↑",x+w-76,row,35,()->{int n=chosen.indexOf(a);if(n>0){Collections.swap(chosen,n,n-1);rebuild();}});button("↓",x+w-38,row,35,()->{int n=chosen.indexOf(a);if(n>=0&&n+1<chosen.size()){Collections.swap(chosen,n,n+1);rebuild();}});}
        button("‹",x,height-65,35,()->{page=Math.floorMod(page-1,pages);rebuild();});button("›",x+w-35,height-65,35,()->{page=(page+1)%pages;rebuild();});button("Сохранить",x,height-40,w/2-3,()->{if(!ModerationService.allowed()){status="У вас нет доступа";return;}try{ModerationFavorites.save(chosen);onClose();}catch(Exception e){status="Не удалось сохранить избранное";}});button("Назад",x+w/2,height-40,w/2,()->onClose());
    }
    @Override public void tick(){if(!ModerationService.allowed())minecraft.gui.setScreen(null);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float dt){g.centeredText(font,"Избранные действия · порядок сохраняется",width/2,16,0xFFFFFFFF);g.centeredText(font,status,width/2,height-80,0xFFAAC7ED);renderWidgets(g,mx,my,dt);}
}
