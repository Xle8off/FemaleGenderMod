import com.wildfire.client.overhead.CameraOptions;
import java.util.*;
public final class CameraOptionsCheck {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        var good=new CameraOptions.Mode(640,480,30);
        var modes=CameraOptions.allowed(List.of(good,good,new CameraOptions.Mode(3840,2160,60),new CameraOptions.Mode(640,480,60),new CameraOptions.Mode(854,480,30),new CameraOptions.Mode(320,240,15),new CameraOptions.Mode(0,0,0)));
        check(modes.size()==3,"real supported modes deduplicated, 4K/60 excluded");
        check(modes.getFirst().width()==854,"best eligible mode first");
        check(modes.stream().allMatch(CameraOptions.Mode::allowed),"UI cannot select above 480p30");
        for(int[] input:List.of(new int[]{3840,2160},new int[]{640,480},new int[]{1080,1920},new int[]{8192,8192})){
            int[] size=CameraOptions.fit(input[0],input[1]);
            check(size[0]<=854&&size[1]<=480&&size[0]%2==0&&size[1]%2==0,"defensive frame scaling stays bounded and even");
        }
        try{CameraOptions.fit(Integer.MAX_VALUE,1);throw new AssertionError("oversized frame accepted");}catch(IllegalArgumentException expected){}
        System.out.println("Camera options: supported modes, deduplication, 480p30 limits and defensive scaling passed");
    }
}
