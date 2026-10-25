package dev.aeltorx.inflow.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Local action IDs only, never command text or permission grants. */
public final class ModerationFavorites {
    public enum Action {
        PANEL("Панель модерации"),CROSSHAIR("Игрок под прицелом"),REPORTS("Очередь жалоб"),MODERATORS("Модераторы"),CARD("Карточка игрока"),INSPECT("Инспектор CoreProtect"),TELEPORT("ТП к игроку"),CUSTOM_RULES("Свои правила"),PUNISH("Наказание"),CHECK("На проверку"),PASS("Проверка пройдена"),HISTORY("История игрока"),PREVIEW("Тест анимаций");
        public final String label;Action(String label){this.label=label;}
    }
    private static final List<Action> DEFAULT=List.of(Action.CROSSHAIR,Action.PANEL,Action.REPORTS,Action.PUNISH);
    private static List<Action> favorites=DEFAULT;
    private static boolean loaded;
    public static List<Action> sanitize(List<String> ids){var out=new ArrayList<Action>();for(String id:ids){if(out.size()>=8)break;try{var a=Action.valueOf(id);if(!out.contains(a))out.add(a);}catch(Exception ignored){}}return List.copyOf(out);}
    private static Path path(){return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("windows-client-wheel.json");}
    private static void load(){if(loaded)return;loaded=true;try{if(!Files.exists(path()))return;if(Files.size(path())>8192)throw new IllegalArgumentException("Preferences too large");var a=JsonParser.parseString(Files.readString(path())).getAsJsonArray();var ids=new ArrayList<String>();for(var e:a)if(e.isJsonPrimitive())ids.add(e.getAsString());favorites=sanitize(ids);}catch(Exception e){org.slf4j.LoggerFactory.getLogger("InFlow").warn("Cannot read wheel favorites; using defaults",e);}}
    public static List<Action> favorites(){load();return favorites;}
    public static List<Action> ordered(){var out=new ArrayList<>(favorites());for(var a:Action.values())if(!out.contains(a))out.add(a);return List.copyOf(out);}
    public static void save(List<Action> selected)throws java.io.IOException {if(selected.size()>8)throw new IllegalArgumentException("Максимум 8 избранных действий");var normalized=sanitize(selected.stream().map(Enum::name).toList());Path p=path();Files.createDirectories(p.getParent());Path tmp=p.resolveSibling(p.getFileName()+".tmp");Files.writeString(tmp,new Gson().toJson(normalized.stream().map(Enum::name).toList()));try{Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}favorites=normalized;loaded=true;}
}
