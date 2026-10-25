package dev.aeltorx.inflow.client;
import net.minecraft.client.Minecraft;
import java.awt.image.BufferedImage;
import java.util.UUID;

/** Display cache only. Editable profile data and avatars are authoritative on the server. */
public final class ModeratorAccount {
    private static UUID identity;private static BufferedImage avatar;private static long revision;
    public static String name(){var p=Minecraft.getInstance().player;return p==null?"Модератор":p.getGameProfile().name();}
    public static BufferedImage avatar(){return avatar;}
    public static long revision(){return revision;}
    public static void useServerAvatar(BufferedImage image){if(avatar!=image){avatar=image;revision++;}}
    public static void ensure(){var p=Minecraft.getInstance().player;if(p!=null&&!p.getUUID().equals(identity)){identity=p.getUUID();avatar=null;revision++;}}
}
