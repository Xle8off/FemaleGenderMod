import com.wildfire.gui.PlayerAssetUpload;
import com.wildfire.gui.ModPresence;
import com.google.gson.JsonParser;
import java.util.concurrent.*;
public final class PlayerAssetsCheck {
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  var skin=PlayerAssetUpload.request("skin",kind->check(kind.equals("skin"),"request kind"));
  check(PlayerAssetUpload.request("cape",kind->{}).isCompletedExceptionally(),"only one pending request");
  PlayerAssetUpload.ticket(JsonParser.parseString("{\"assetTicket\":true,\"kind\":\"cape\",\"token\":\""+ "a".repeat(64)+"\"}").getAsJsonObject());
  check(!skin.isDone(),"different asset cannot satisfy pending request");
  PlayerAssetUpload.ticket(JsonParser.parseString("{\"assetTicket\":true,\"kind\":\"skin\",\"token\":\"bad\"}").getAsJsonObject());
  check(skin.isCompletedExceptionally(),"invalid ticket rejected");
  var cape=PlayerAssetUpload.request("cape",kind->{});
  PlayerAssetUpload.ticket(JsonParser.parseString("{\"assetTicket\":true,\"kind\":\"cape\",\"token\":\""+ "b".repeat(64)+"\"}").getAsJsonObject());
  check(cape.get(1,TimeUnit.SECONDS).equals("b".repeat(64)),"valid scoped grant accepted");
  ModPresence.accept(JsonParser.parseString("{\"friends\":[{\"nickname\":\"TestPlayer\",\"hasMod\":true}]}"));
  check(ModPresence.has("testplayer"),"server capability accepted case insensitively");
  ModPresence.accept(JsonParser.parseString("{\"partner\":\"TestPlayer\",\"hasMod\":false}"));
  check(!ModPresence.has("TestPlayer"),"server removal clears badge");
  ModPresence.accept(JsonParser.parseString("{\"nickname\":\"Other\",\"online\":true}"));
  check(!ModPresence.has("Other"),"online alone is not a mod capability");
  System.out.println("Player assets: scoped grants, in-flight guard, invalid tokens; presence snapshot and no inference passed");
 }
}
