public final class PortNativeDialogsCheck {
 public static void main(String[] args)throws Exception{
  var dialog=Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
  var version=dialog.getField("tinyfd_version").get(null);
  if(version==null)throw new AssertionError("Native picker did not initialize");
  System.out.println("26.3 native file picker initialized: "+version);
 }
}
