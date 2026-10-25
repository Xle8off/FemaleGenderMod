package dev.aeltorx.inflow.client;

import java.util.List;

/** Rule numbers are the complete server-side punishment reason. */
public final class ModerationRules {
    public record Rule(String number, String title, int section) { }
    private static final List<Rule> BUILTIN = List.of(
        new Rule("1.1", "Уважение и конфликты", 1),
        new Rule("1.2", "Мат и засорение чата", 1),
        new Rule("1.3", "Оскорбления, травля, угрозы", 1),
        new Rule("1.4", "Флуд, спам и капс", 1),
        new Rule("1.5", "NSFW, ссылки, личные данные, реклама", 1),
        new Rule("1.6", "Конфликтные обсуждения", 1),
        new Rule("1.7", "Нацизм, расизм, дискриминация", 1),
        new Rule("2.1", "Гриферство и кража", 2),
        new Rule("2.2", "Использование чужого имущества", 2),
        new Rule("2.3", "Убийство без согласия", 2),
        new Rule("2.4", "Намеренные помехи игрокам", 2),
        new Rule("2.5", "Возврат и восстановление ущерба", 2),
        new Rule("2.6", "Мошенничество при сделках", 2),
        new Rule("2.7", "Торговля за реальные деньги", 2),
        new Rule("5.5", "Читы и нечестное преимущество", 5),
        new Rule("5.6", "Боты и полная автоматизация", 5),
        new Rule("5.7", "Баги, уязвимости и запрещённые дюпы", 5),
        new Rule("5.8", "Нагрузка, помехи, нарушение баланса", 5));
    public static final List<Rule> ALL = new java.util.ArrayList<>(BUILTIN);
    private static java.nio.file.Path file(){return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("windows-client-rules.json");}
    public static void load(){
        var FILE=file();
        if(!java.nio.file.Files.exists(FILE))return;
        try(var reader=java.nio.file.Files.newBufferedReader(FILE)){
            Rule[] rules=new com.google.gson.Gson().fromJson(reader,Rule[].class);
            if(rules!=null)for(Rule r:rules)if(valid(r)&&ALL.size()<82&&!ALL.stream().anyMatch(old->old.number().equals(r.number())))ALL.add(r);
        }catch(Exception e){org.slf4j.LoggerFactory.getLogger("InFlow").warn("Cannot read custom moderation rules",e);}
    }
    private static boolean valid(Rule r){return r!=null&&r.number()!=null&&r.number().matches("[0-9]{1,3}\\.[0-9]{1,3}")
        &&r.title()!=null&&!r.title().isBlank()&&r.title().length()<=80&&!r.title().contains("\n")
        &&(r.section()==1||r.section()==2||r.section()==5)&&(!r.number().startsWith("5.")||r.section()==5);}
    public static void save(Rule r,boolean remove) throws java.io.IOException{
        var FILE=file();
        if(!valid(r))throw new IllegalArgumentException("Номер: 9.1; описание до 80 символов; 5.x — только бан");
        if(BUILTIN.stream().anyMatch(old->old.number().equals(r.number())))throw new IllegalArgumentException("Встроенные правила нельзя перезаписать");
        var next=new java.util.ArrayList<>(ALL);next.removeIf(old->old.number().equals(r.number()));
        if(!remove){if(next.size()>=82)throw new IllegalArgumentException("Лимит: 64 своих правила");next.add(r);}
        java.nio.file.Files.createDirectories(FILE.getParent());
        var temp=FILE.resolveSibling(FILE.getFileName()+".tmp");
        java.nio.file.Files.writeString(temp,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(next.stream().filter(old->!BUILTIN.contains(old)).toList()));
        java.nio.file.Files.move(temp,FILE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ALL.clear();ALL.addAll(next);
    }
    public static final List<String> SHORT_TIMES = List.of("1m", "30m", "1h", "4h", "1d");
    public static final List<String> BAN_TIMES = List.of("1m", "1d", "16d", "30d", "permanent");
    private ModerationRules() { }
}
