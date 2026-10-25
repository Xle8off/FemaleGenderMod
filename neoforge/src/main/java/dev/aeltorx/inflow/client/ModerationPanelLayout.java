package dev.aeltorx.inflow.client;
/** Fixed logical workspace. Only the complete canvas scales with the game window. */
public final class ModerationPanelLayout {
 public static final int CONTENT_X=140,CONTENT_RIGHT=12,PROFILE_Y=42,PROFILE_H=46,ACTIONS_Y=98,HELP_Y=156,BODY_Y=182;
 private ModerationPanelLayout(){}
 public static int contentWidth(int width){return width-CONTENT_X-CONTENT_RIGHT;}
 public static ModerationWindowBounds.Rect nav(int index){return new ModerationWindowBounds.Rect(8,48+index*25,112,21);}
 public static ModerationWindowBounds.Rect action(int index,int width){int w=contentWidth(width),gap=4,usable=w-2*gap,col=index%3,start=col*usable/3,end=(col+1)*usable/3;return new ModerationWindowBounds.Rect(CONTENT_X+start+col*gap,ACTIONS_Y+index/3*24,end-start,20);}
}
