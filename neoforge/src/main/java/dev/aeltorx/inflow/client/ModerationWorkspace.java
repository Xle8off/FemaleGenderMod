package dev.aeltorx.inflow.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.Consumer;
import java.nio.file.*;

/** Client adapter only; identity, permissions, case revisions and results belong to the server. */
public final class ModerationWorkspace {
    private static final Map<Integer,Waiting> callbacks=new HashMap<>();
    private record Waiting(long until,String operation,Consumer<JsonObject> callback){}
    private static long lastReply,lastSend;
    private static Object advertisedConnection;
    public static void receivePayload(ModerationReplyPayload payload){Minecraft.getInstance().execute(()->receive(payload.json()));}
    private static int sequence;private static Object connection;private static long helloAt;private static boolean ready,welcomed;
    public static JsonObject summary=new JsonObject();
    public static ModerationPanelScreen minimized;
    public static boolean ready(){return ready&&connection==Minecraft.getInstance().getConnection();}
    public static boolean connected(){return Minecraft.getInstance().getConnection()!=null&&Minecraft.getInstance().getConnection().hasChannel(ModerationRequestPayload.TYPE);}
    public static void register(){
        // Native optional payload handlers are installed by InFlowClient on the mod event bus.
    }
    public static void tick(){
        var mc=Minecraft.getInstance();if(connection!=mc.getConnection()){connection=mc.getConnection();SharedModeratorProfiles.reset();ready=false;welcomed=false;summary=new JsonObject();minimized=null;callbacks.clear();helloAt=0;advertisedConnection=null;lastReply=0;lastSend=0;}
        long now=System.currentTimeMillis();
        var expired=callbacks.entrySet().stream().filter(e->e.getValue().until<now).toList();for(var e:expired){callbacks.remove(e.getKey());LOG.warn("Request #{} op={} timed out",e.getKey(),e.getValue().operation);if(now-lastReply>20000){ready=false;advertisedConnection=null;}try{e.getValue().callback.accept(error("Сервер не ответил на "+e.getValue().operation+"; действие не подтверждено"));}catch(Exception ex){LOG.error("Timeout callback failed",ex);}}
        if(connected()&&callbacks.isEmpty()&&now>helloAt&&now-lastSend>250){helloAt=now+(ready?30000:10000);advertiseReplyChannel();request("hello",new JsonObject(),q->{if(q.has("data")&&q.get("kind").getAsString().equals("summary")){summary=q.getAsJsonObject("data");ready=summary.has("protocol")&&summary.get("protocol").getAsInt()==1;if(ready)SharedModeratorProfiles.syncSelf();if(!welcomed){welcomed=true;ModerationService.notice(ready?"Панель синхронизирована: открытых дел "+summary.get("openCases")+", проверок "+summary.get("checks")+". F9 → Панель модерации.":"Несовместимый серверный протокол модерации");}}});}
    }
    private static void advertiseReplyChannel(){advertisedConnection=connection;}
    public static void request(String op,JsonObject params,Consumer<JsonObject> callback){
        if(!connected()){callback.accept(error("Сервер не зарегистрировал канал панели. Нужен серверный модуль InFlow и переподключение"));return;}
        JsonObject q=params.deepCopy();int id=++sequence;q.addProperty("id",id);q.addProperty("op",op);
        try{dev.aeltorx.inflow.common.ModerationWire.encode(q.toString());callbacks.put(id,new Waiting(System.currentTimeMillis()+(op.equals("file-chunk")?240000:op.equals("hello")?8000:20000),op,callback));lastSend=System.currentTimeMillis();if(!op.startsWith("file-"))LOG.info("Send request #{} op={}",id,op);net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new ModerationRequestPayload(q.toString()));}
        catch(Exception e){callbacks.remove(id);LOG.error("Could not send moderation request #{} op={}",id,op,e);callback.accept(error("Не удалось отправить запрос"));}
    }
    private static JsonObject error(String message){var q=new JsonObject();q.addProperty("kind","error");q.addProperty("message",message);return q;}
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("InFlow/Moderation");
    private static void receive(String json){
        JsonObject q;int id;
        try{dev.aeltorx.inflow.common.ModerationWire.checkJsonDepth(json);q=JsonParser.parseString(json).getAsJsonObject();id=q.get("id").getAsInt();}
        catch(Exception e){LOG.error("Invalid moderation reply (payload omitted)",e);ModerationService.notice("Некорректный ответ панели; смотри latest.log");return;}
        lastReply=System.currentTimeMillis();var w=callbacks.remove(id);if(w==null){LOG.debug("Late/duplicate moderation reply #{} ignored",id);return;}
        if(!w.operation.startsWith("file-")){LOG.info("Receive reply #{} op={} kind={}",id,w.operation,q.has("kind")?q.get("kind").getAsString():"missing");if(q.has("kind")&&q.get("kind").getAsString().equals("error"))LOG.warn("Server rejected request #{}: {}",id,q.has("message")?q.get("message").getAsString():"no message");}
        try{w.callback.accept(q);}catch(Exception e){LOG.error("Moderation reply handler failed, request {}",id,e);ModerationService.notice("Ошибка отображения ответа #"+id+"; смотри latest.log");}
    }
    public static void preparePunishment(ModerationService.Target target,List<String> commands,LocalModerationAnimation.Style style){
        var q=new JsonObject();q.addProperty("target",target.id().toString());q.addProperty("warn",false);q.addProperty("mute",false);q.addProperty("ban",false);q.addProperty("muteTime","30m");q.addProperty("banTime","1d");q.addProperty("style",style.name());
        for(String command:commands){String[] p=command.split(" ");switch(p[0]){case "warn"->{q.addProperty("warn",true);q.addProperty("rule",p[2]);}case "mute"->{q.addProperty("mute",true);q.addProperty("muteTime",p[2]);q.addProperty("rule",p[3]);}case "ban"->{q.addProperty("ban",true);q.addProperty("banTime","permanent");q.addProperty("rule",p[2]);}case "tempban"->{q.addProperty("ban",true);q.addProperty("banTime",p[2]);q.addProperty("rule",p[3]);}default->{ModerationService.notice("Сочетание команд не поддержано");return;}}}
        request("plan",q,r->{if(!r.get("kind").getAsString().equals("plan")){ModerationService.notice(r.get("message").getAsString());return;}Minecraft.getInstance().gui.setScreen(new PunishmentReviewScreen(target,commands,style,r.getAsJsonObject("data").get("token").getAsString()));});
    }
    public static void present(ModerationPanelScreen panel){if(!ModerationService.allowed()){ModerationService.notice("У вас нет доступа");return;}if(!InFlowWelcomeScreen.intercept(panel)){panel.appear();Minecraft.getInstance().gui.setScreen(panel);}}
    public static void open(net.minecraft.client.gui.screens.Screen parent){var mc=Minecraft.getInstance();if(mc.gui.screen() instanceof InFlowWelcomeScreen welcome){welcome.onClose();return;}if(minimized!=null){var screen=minimized;minimized=null;present(screen);}else present(new ModerationPanelScreen(parent));}
    public static final int MAX_FILE=128*1024*1024;
    private static final java.util.concurrent.ExecutorService FILE_IO=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"wc-evidence-read");t.setDaemon(true);return t;});
    public static void download(String target,String file,Consumer<String> status){
        var mc=Minecraft.getInstance();Object session=mc.getConnection();
        Path directory=mc.gameDirectory.toPath().resolve("windowsclient-evidence");
        FILE_IO.execute(()->{try{Files.createDirectories(directory);Path temp=Files.createTempFile(directory,"download-",".part");mc.execute(()->downloadChunk(target,file,0,0,temp,session,status));}catch(Exception e){mc.execute(()->status.accept("Не удалось создать файл загрузки"));}});
    }
    private static void downloadChunk(String target,String file,int offset,int expected,Path temp,Object session,Consumer<String> status){
        var mc=Minecraft.getInstance();if(mc.getConnection()!=session){status.accept("Смена сервера: загрузка прервана");return;}var q=new JsonObject();q.addProperty("target",target);q.addProperty("file",file);q.addProperty("offset",offset);
        request("file-read",q,r->{if(!r.get("kind").getAsString().equals("file-data")){status.accept(r.get("message").getAsString());return;}
            FILE_IO.execute(()->{try{var d=r.getAsJsonObject("data");int size=d.get("size").getAsInt();byte[] part=Base64.getDecoder().decode(d.get("bytes").getAsString());String name=d.get("name").getAsString();
                if(size<1||size>MAX_FILE||expected!=0&&size!=expected||offset!=d.get("offset").getAsInt()||part.length<1||part.length>16384||offset+part.length>size||!name.matches("[A-Za-z0-9А-Яа-яЁё _.-]{1,90}\\.(png|jpg|jpeg|webp|mp4|webm)"))throw new java.io.IOException("Invalid file reply");
                Files.write(temp,part,StandardOpenOption.APPEND);int next=offset+part.length;
                if(next==size){Path saved=temp.resolveSibling(UUID.randomUUID()+"-"+name);Files.move(temp,saved);mc.execute(()->status.accept("Скачано: "+saved));}
                else mc.execute(()->{status.accept("Загрузка с сервера: "+next*100L/size+"%");downloadChunk(target,file,next,size,temp,session,status);});
            }catch(Exception e){mc.execute(()->status.accept("Загрузка прервана: неверный ответ или ошибка записи"));}});
        });
    }
    public static final int MAX_FILES=10;
    public static void upload(Path file,String target,String rule,String description,Consumer<String> status){
        uploadBatch(List.of(file),target,rule,status,()->{});
    }
    public static void uploadBatch(List<Path> selected,String target,String rule,Consumer<String> status,Runnable finished){
        var files=List.copyOf(selected);var mc=Minecraft.getInstance();Object session=mc.getConnection();
        if(!summary.has("evidenceBatch")||!summary.get("evidenceBatch").getAsBoolean()){status.accept("Для вложений нужен серверный серверный модуль InFlow");finished.run();return;}
        if(files.isEmpty()||files.size()>MAX_FILES){status.accept("Выбери от 1 до 10 файлов");finished.run();return;}
        if(!dev.aeltorx.inflow.common.ModerationWire.rule(rule)){status.accept("Укажи номер правила, например 2.1");finished.run();return;}
        FILE_IO.execute(()->{try{
            var sizes=new ArrayList<Long>();var manifest=new JsonArray();long total=0;
            for(Path file:files){
                long size=Files.size(file);
                if(!Files.isRegularFile(file)||size<1||size>MAX_FILE)throw new IllegalArgumentException("Каждый файл должен быть от 1 байта до 128 МиБ");
                if(!file.getFileName().toString().matches("(?i)[A-Za-z0-9А-Яа-яЁё _.-]{1,90}\\.(png|jpg|jpeg|webp|mp4|webm)"))throw new IllegalArgumentException("Неподдерживаемый тип или имя файла: "+file.getFileName());
                total+=size;if(total>MAX_FILE)throw new IllegalArgumentException("Суммарный размер вложений — не больше 128 МиБ");
                sizes.add(size);var item=new JsonObject();item.addProperty("filename",file.getFileName().toString());item.addProperty("size",size);manifest.add(item);
            }
            mc.execute(()->{
                if(session!=mc.getConnection()){status.accept("Смена сервера — загрузка отменена");finished.run();return;}
                var q=new JsonObject();q.addProperty("target",target);q.addProperty("rule",rule);q.add("files",manifest);
                status.accept("Подготовка "+files.size()+" вложений…");
                request("file-start",q,r->{
                    if(r.get("kind").getAsString().equals("file-ready"))sendBatchChunk(files,sizes,0,0,r.getAsJsonObject("data").get("token").getAsString(),session,status,finished);
                    else {status.accept(r.get("message").getAsString());finished.run();}
                });
            });
        }catch(Exception e){mc.execute(()->{status.accept(e instanceof IllegalArgumentException?e.getMessage():"Не удалось прочитать файлы");finished.run();});}});
    }
    private static void sendBatchChunk(List<Path> files,List<Long> sizes,int index,int offset,String token,Object session,Consumer<String> status,Runnable finished){
        var mc=Minecraft.getInstance();if(session!=mc.getConnection()){status.accept("Смена сервера — загрузка отменена");finished.run();return;}
        Path file=files.get(index);long size=sizes.get(index);
        FILE_IO.execute(()->{try{
            if(Files.size(file)!=size)throw new java.io.IOException("File changed");
            int count=(int)Math.min(size-offset,16384);byte[] bytes=new byte[count];
            try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.READ)){channel.position(offset);var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new java.io.EOFException();}
            mc.execute(()->{
                if(session!=mc.getConnection()){status.accept("Смена сервера — загрузка отменена");finished.run();return;}
                var q=new JsonObject();q.addProperty("token",token);q.addProperty("fileIndex",index);q.addProperty("offset",offset);q.addProperty("bytes",Base64.getEncoder().encodeToString(bytes));
                status.accept("Файл "+(index+1)+"/"+files.size()+": "+(offset+count)*100L/size+"%"+(index==files.size()-1&&offset+count==size?" · сохранение и отправка в Discord…":""));
                request("file-chunk",q,r->{
                    if(r.get("kind").getAsString().equals("file-next")){
                        int next=offset+count;sendBatchChunk(files,sizes,next==size?index+1:index,next==size?0:next,token,session,status,finished);
                    }else {status.accept(r.get("message").getAsString());finished.run();}
                });
            });
        }catch(Exception e){mc.execute(()->{status.accept("Чтение прервано: файл изменён или недоступен");finished.run();});}});
    }
    public static void chooseFiles(Consumer<List<Path>> chosen){java.util.concurrent.CompletableFuture.runAsync(()->{try(var stack=org.lwjgl.system.MemoryStack.stackPush()){var filters=stack.pointers(stack.UTF8("*.png"),stack.UTF8("*.jpg"),stack.UTF8("*.jpeg"),stack.UTF8("*.webp"),stack.UTF8("*.mp4"),stack.UTF8("*.webm"));String selected=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Доказательства — до 10 файлов, суммарно 128 МиБ","",filters,"Изображения и видео",true);if(selected!=null){var paths=Arrays.stream(selected.split("\\|")).map(Path::of).toList();Minecraft.getInstance().execute(()->chosen.accept(paths));}}catch(Exception e){Minecraft.getInstance().execute(()->ModerationService.notice("Не удалось открыть выбор файлов"));}});}
    public static void chooseFile(Consumer<Path> chosen){chooseFiles(files->{if(!files.isEmpty())chosen.accept(files.getFirst());});}
}
