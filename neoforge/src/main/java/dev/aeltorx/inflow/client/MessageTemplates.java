package dev.aeltorx.inflow.client;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public final class MessageTemplates {
    public static final List<String> TITLES=List.of("Ожидай проверки","Пришли Discord","Объясни ситуацию","Проверка завершена");
    private static final List<String> DEFAULT=List.of("{player}, ожидай проверки. Не выходи с сервера. Твой проверяющий: {moderator}.","{player}, напиши свой ник Discord в чат проверки. Сообщение увидит только проверяющий.","{player}, объясни проверяющему, что произошло.","{player}, проверка завершена. Спасибо за сотрудничество.");
    private static List<String> texts=new ArrayList<>(DEFAULT);private static boolean loaded;
    private static Path path(){return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("windows-client-messages.json");}
    public static String validate(String value){String text=Objects.requireNonNull(value).trim();if(text.isEmpty()||text.length()>500||text.chars().anyMatch(c->Character.isISOControl(c)||c==0x00a7))throw new IllegalArgumentException("Текст: 1–500 символов");return text;}
    public static String expand(String text,String player,String moderator){return validate(text.replace("{player}",player).replace("{moderator}",moderator));}
    public static String get(int index){load();return texts.get(index);}
    private static void load(){if(loaded)return;loaded=true;try{if(!Files.exists(path()))return;if(Files.size(path())>16384)throw new IllegalArgumentException("Templates too large");var a=JsonParser.parseString(Files.readString(path())).getAsJsonArray();if(a.size()!=4)throw new IllegalArgumentException("Invalid template count");var result=new ArrayList<String>();for(var e:a)result.add(validate(e.getAsString()));texts=result;}catch(Exception e){org.slf4j.LoggerFactory.getLogger("InFlow").warn("Cannot load message templates; using defaults",e);}}
    public static void save(int index,String value)throws java.io.IOException{load();String text=validate(value);var next=new ArrayList<>(texts);next.set(index,text);Path p=path();Files.createDirectories(p.getParent());Path tmp=p.resolveSibling(p.getFileName()+".tmp");Files.writeString(tmp,new Gson().toJson(next));try{Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}texts=next;}
}
