import com.wildfire.gui.CameraViewerLabels;
public final class CameraViewerLabelsCheck {
    public static void main(String[] args){
        String detail=com.wildfire.gui.CameraSignalDiagnostics.format("PUBLISH_TRACK","UNAUTHORIZED",401);
        if(!detail.contains("401")||!detail.contains("UNAUTHORIZED")||!detail.contains("Публикация"))throw new AssertionError("Missing phase/HTTP/code");
        String hidden=com.wildfire.gui.CameraSignalDiagnostics.format("PRIVATE_HOST","SECRET_TOKEN",999);
        if(hidden.contains("PRIVATE_HOST")||hidden.contains("SECRET_TOKEN")||hidden.contains("999"))throw new AssertionError("Untrusted diagnostic fields leaked");
        if(!CameraViewerLabels.status("RECEIVING",100).equals("Получает видео"))throw new AssertionError("Frame confirmation missing");
        if(CameraViewerLabels.status("RECEIVING",9000).equals("Получает видео"))throw new AssertionError("Stale report shown live");
        if(CameraViewerLabels.status("UNCONFIRMED",0).equals("Получает видео"))throw new AssertionError("Old clients shown live");
        if(!CameraViewerLabels.error("https://private-address/sdp").isEmpty())throw new AssertionError("Unknown diagnostic leaks transport text");
        for(String code:new String[]{"SUBSCRIBE_FAILED","PEER_INIT_FAILED","ANSWER_FAILED","ANSWER_REJECTED","ICE_FAILED","CONNECT_TIMEOUT"})if(CameraViewerLabels.error(code).isEmpty())throw new AssertionError("Missing safe error label");
        System.out.println("PASS: live/stale/unconfirmed viewer labels and controlled error text");
    }
}
