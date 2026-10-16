import dev.onvoid.webrtc.internal.NativeLoader;
import dev.onvoid.webrtc.media.video.*;
import dev.onvoid.webrtc.media.FourCC;
/** Synthetic buffers only: never enumerate or open cameras/microphones in tests. */
public final class CameraNativeCheck {
    public static void main(String[] args)throws Exception {
        NativeLoader.loadLibrary("webrtc-java");
        var buffer=NativeI420Buffer.allocate(16,16);
        try{
            var y=buffer.getDataY();var u=buffer.getDataU();var v=buffer.getDataV();
            while(y.hasRemaining())y.put((byte)16);while(u.hasRemaining())u.put((byte)128);while(v.hasRemaining())v.put((byte)128);
            byte[] rgba=new byte[16*16*4];VideoBufferConverter.convertFromI420(buffer,rgba,FourCC.ABGR);
            if((rgba[0]&255)>2||(rgba[1]&255)>2||(rgba[2]&255)>2||(rgba[3]&255)!=255)throw new AssertionError("Unexpected native RGBA layout");
            // Regression: unscaled cropAndScale returns a wrapper without a native handle.
            for(int n=0;n<100;n++){
                var same=com.wildfire.client.overhead.CameraBuffers.resize(buffer,16,16);
                try{VideoBufferConverter.convertFromI420(same,rgba,FourCC.ABGR);}finally{same.release();}
            }
            // Original reference must still be usable after releasing every temporary reference.
            VideoBufferConverter.convertFromI420(buffer,rgba,FourCC.ABGR);
            var scaled=com.wildfire.client.overhead.CameraBuffers.resize(buffer,8,8);
            try{if(scaled.getWidth()!=8||scaled.getHeight()!=8)throw new AssertionError("Native scale failed");}finally{scaled.release();}
        }finally{buffer.release();}
        System.out.println("Native WebRTC: library load, synthetic I420 -> RGBA and scaling passed; no devices opened");
    }
}
