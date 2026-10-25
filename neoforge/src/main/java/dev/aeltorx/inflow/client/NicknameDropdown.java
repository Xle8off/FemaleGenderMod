package dev.aeltorx.inflow.client;
import java.util.*;
import java.util.function.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import net.minecraft.locale.Language;
/** Overlay owned by the text field; it never joins normal button keyboard navigation. */
public final class NicknameDropdown {
 private static final int ROW=20,VISIBLE=6;
 private final EditBox field;private final Supplier<List<String>> source;private final Consumer<String> accepted;
 private List<String> matches=List.of();private String query="\u0000";private int selected=-1,offset;
 private List<String> sourceSnapshot;
 private boolean dismissed;private int lastX=Integer.MIN_VALUE,lastY=Integer.MIN_VALUE;
 public NicknameDropdown(EditBox field,Supplier<List<String>> source,Consumer<String> accepted){this.field=field;this.source=source;this.accepted=accepted;}
 private void update(){String value=field.getValue();boolean changed=!value.equals(query);if(changed){query=value;selected=-1;offset=0;dismissed=false;}var names=source.get();if(!changed&&names.equals(sourceSnapshot))return;sourceSnapshot=new ArrayList<>(names);var next=NicknameMatches.find(names,value,128);if(!next.equals(matches)){matches=next;selected=-1;offset=Math.min(offset,Math.max(0,matches.size()-VISIBLE));}}
 private boolean open(){return field.isFocused()&&!dismissed&&!matches.isEmpty();}
 private int x(){return field.getX()-6;}private int y(){return field.getY()+field.getHeight()+2;}private int width(){return field.getWidth()+12;}
 private int count(){return Math.min(VISIBLE,matches.size()-offset);}
 private boolean contains(double x,double y){return x>=x()&&x<x()+width()&&y>=y()&&y<y()+count()*ROW+2;}
 private void choose(boolean submit){if(selected<0||selected>=matches.size())return;String name=matches.get(selected);field.setValue(name);field.setCursorPosition(name.length());field.setHighlightPos(name.length());query=name;sourceSnapshot=null;dismissed=true;if(submit)accepted.accept(name);}
 public boolean keyPressed(KeyEvent event){update();if(!open())return false;int key=event.key();
  if(key==InputConstants.KEY_ESCAPE){dismissed=true;return true;}
  if(key==InputConstants.KEY_DOWN||key==InputConstants.KEY_UP){selected=selected<0?(key==InputConstants.KEY_DOWN?0:matches.size()-1):Math.clamp(selected+(key==InputConstants.KEY_DOWN?1:-1),0,matches.size()-1);offset=Math.clamp(offset,Math.max(0,selected-VISIBLE+1),selected);return true;}
  if(key==InputConstants.KEY_TAB){if(selected<0)selected=0;choose(false);return true;}
  if((key==InputConstants.KEY_RETURN||key==InputConstants.KEY_NUMPADENTER)&&selected>=0){choose(true);return true;}
  return false;
 }
 public boolean click(MouseButtonEvent event){update();if(open()&&contains(event.x(),event.y())){if(event.button()==InputConstants.MOUSE_BUTTON_LEFT){int row=(int)(event.y()-y())/ROW;if(row<count()){selected=offset+row;choose(true);}}return true;}if(field.isMouseOver(event.x(),event.y()))dismissed=false;else dismissed=true;return false;}
 public boolean scroll(double x,double y,double amount){update();if(!open()||!contains(x,y))return false;if(amount!=0){offset=Math.clamp(offset+(amount<0?1:-1),0,Math.max(0,matches.size()-VISIBLE));selected=-1;}return true;}
 public void draw(GuiGraphicsExtractor g,int mx,int my){update();if(!open())return;var font=Minecraft.getInstance().font;ModerationSurfaces.panel(g,x(),y(),width(),count()*ROW+2);
  if(mx!=lastX||my!=lastY){lastX=mx;lastY=my;if(contains(mx,my)){int row=(my-y())/ROW;if(row<count())selected=offset+row;}}
  for(int row=0;row<count();row++){int index=offset+row,yy=y()+1+row*ROW;String name=matches.get(index);if(index==selected)g.fill(x()+1,yy,x()+width()-1,yy+ROW,ModerationTheme.HOVER.getRGB());ModerationPeople.head(g,name,x()+4,yy+2,16);var label=Language.getInstance().getVisualOrder(font.substrByWidth(ModerationPeople.name(name),width()-32));g.text(font,label,x()+25,yy+6,ModerationTheme.TEXT.getRGB(),false);}
  if(matches.size()>VISIBLE){int track=count()*ROW,thumb=Math.max(8,track*VISIBLE/matches.size()),top=y()+1+(track-thumb)*offset/Math.max(1,matches.size()-VISIBLE);g.fill(x()+width()-3,top,x()+width()-2,top+thumb,ModerationTheme.ACCENT.getRGB());}
 }
}
