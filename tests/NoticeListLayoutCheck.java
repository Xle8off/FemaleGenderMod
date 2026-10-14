import com.wildfire.neoforge.client.NoticeListLayout;
public class NoticeListLayoutCheck {
 public static void main(String[] args){
  for(int height:new int[]{1080,540,480,360,270,216,180}){
   var top=NoticeListLayout.create(height,100,0);
   check(top.top()==36&&top.bottom()==height-46,"viewport margins");
   check(top.hit(35)==-1&&top.hit(height-45)==-1,"header/footer must not select");
   check(top.hit(36)==0&&top.hit(71)==0&&top.hit(72)==-1&&top.hit(74)==1,"cards and gaps");
   check(top.rowTop(top.end()-1)<top.bottom(),"last visible card");
   check(top.rowTop(top.end())>=top.bottom(),"no hidden card in read range");
   var bottom=NoticeListLayout.create(height,100,Integer.MAX_VALUE);
   check(bottom.end()==100,"all entries reachable");
   check(bottom.hit(bottom.bottom()-1)==99,"last card clickable at bottom");
   check(bottom.rowTop(99)+36==bottom.bottom(),"no reserved pager gap");
   var empty=NoticeListLayout.create(height,0,999);
   check(empty.offset()==0&&empty.end()==0&&empty.hit(40)==-1,"empty list");
  }
  check(NoticeListLayout.create(540,100,0).end()>6,"no six-entry cap");
  System.out.println("Notification scrolling checks passed at GUI heights 180..1080");
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
