import com.wildfire.gui.FriendListLayout;
public final class FriendListLayoutCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[]args) {
        for(int height:new int[]{180,240,300,360,480,720,1080})for(int count:new int[]{0,1,2,6,20,128}) {
            var initial=FriendListLayout.create(height,count,0);
            for(int requested:new int[]{-38,0,1,19,38,76,initial.maxOffset(),Integer.MAX_VALUE}) {
                var layout=FriendListLayout.create(height,count,requested);
                check(layout.offset()>=0&&layout.offset()<=layout.maxOffset(),"scroll clamped");
                check(layout.slots()<=27,"viewport widget pool bounded");
                for(int index=0;index<count;index++) {
                    int y=layout.rowTop(index);
                    if(y+36>83 && y<83+layout.viewport())check(index>=layout.first()&&index<layout.first()+layout.slots(),"every visible row represented");
                    if(layout.buttonVisible(index))check(y+9>=83&&y+29<=83+layout.viewport(),"no button hitbox outside viewport");
                }
            }
            if(count>0&&initial.maxOffset()>0) {
                var bottom=FriendListLayout.create(height,count,Integer.MAX_VALUE);
                check(bottom.rowTop(count-1)+38==83+bottom.viewport(),"last row reachable at bottom");
                check(bottom.buttonVisible(count-1)||bottom.viewport()<29,"last row actions reachable");
            }
        }
        System.out.println("Friend list checks passed: all GUI scales, bounded row pool, scrolling, clipped hitboxes, last row reachable");
    }
}
