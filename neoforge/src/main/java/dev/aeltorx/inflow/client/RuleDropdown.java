package dev.aeltorx.inflow.client;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

/** Local rule selector. Selection only changes the form, never sends a sanction. */
public final class RuleDropdown {
 public static final List<String> RULES;
 static {var rules=new ArrayList<String>();int[] counts={8,8,17,9,9,8,8,8,9,10};for(int section=0;section<counts.length;section++)for(int item=1;item<=counts[section];item++)rules.add((section+1)+"."+item);RULES=List.copyOf(rules);}
 private static final int ROW=18,VISIBLE=7;
 private final Button anchor;private final Consumer<String> chosen;private final List<String> options;private boolean open;private int offset,selected;
 public RuleDropdown(Button anchor,String value,Consumer<String> chosen){this(anchor,value,RULES,chosen);}
 public RuleDropdown(Button anchor,String value,List<String> options,Consumer<String> chosen){if(options.isEmpty())throw new IllegalArgumentException("Empty selector");this.anchor=anchor;this.chosen=chosen;this.options=List.copyOf(options);selected=Math.max(0,options.indexOf(value));offset=Math.clamp(selected-3,0,Math.max(0,options.size()-VISIBLE));}
 private int visible(){return Math.min(VISIBLE,options.size());}
 private boolean above;
 public RuleDropdown above(){above=true;return this;}
 public void close(){open=false;}
 public void toggle(){open=!open;}
 private int x(){return anchor.getX();}private int y(){return above?anchor.getY()-visible()*ROW-4:anchor.getY()+anchor.getHeight()+2;}
 private boolean contains(double mx,double my){return mx>=x()&&mx<x()+anchor.getWidth()&&my>=y()&&my<y()+visible()*ROW+2;}
 private void choose(){String value=options.get(selected);open=false;anchor.setMessage(Component.literal(value+" ▾"));chosen.accept(value);}
 public boolean click(MouseButtonEvent e){if(!open)return false;if(contains(e.x(),e.y())){if(e.button()==InputConstants.MOUSE_BUTTON_LEFT){selected=Math.min(options.size()-1,offset+(int)(e.y()-y())/ROW);choose();}return true;}if(!anchor.isMouseOver(e.x(),e.y()))open=false;return false;}
 public boolean scroll(double mx,double my,double amount){if(!open||!contains(mx,my))return false;offset=Math.clamp(offset+(amount<0?1:amount>0?-1:0),0,options.size()-visible());return true;}
 public boolean keyPressed(KeyEvent e){if(!open)return false;int key=e.key();if(key==InputConstants.KEY_ESCAPE){open=false;return true;}if(key==InputConstants.KEY_UP||key==InputConstants.KEY_DOWN){selected=Math.clamp(selected+(key==InputConstants.KEY_DOWN?1:-1),0,options.size()-1);offset=Math.clamp(offset,Math.max(0,selected-visible()+1),Math.min(selected,options.size()-visible()));return true;}if(key==InputConstants.KEY_RETURN||key==InputConstants.KEY_NUMPADENTER){choose();return true;}if(key==InputConstants.KEY_TAB){open=false;}return false;}
 public void draw(GuiGraphicsExtractor g,int mx,int my){if(!open)return;ModerationSurfaces.panel(g,x(),y(),anchor.getWidth(),visible()*ROW+2);var font=Minecraft.getInstance().font;for(int row=0;row<visible();row++){int yy=y()+1+row*ROW,index=offset+row;boolean hover=mx>=x()&&mx<x()+anchor.getWidth()&&my>=yy&&my<yy+ROW;if(hover||index==selected)g.fill(x()+1,yy,x()+anchor.getWidth()-1,yy+ROW,ModerationTheme.HOVER.getRGB());g.text(font,options.get(index),x()+7,yy+5,ModerationTheme.TEXT.getRGB(),false);}if(options.size()>visible()){int track=visible()*ROW,thumb=Math.max(8,track*visible()/options.size()),top=y()+1+(track-thumb)*offset/(options.size()-visible());g.fill(x()+anchor.getWidth()-3,top,x()+anchor.getWidth()-2,top+thumb,ModerationTheme.ACCENT.getRGB());}}
}
