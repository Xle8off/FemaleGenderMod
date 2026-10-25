package dev.aeltorx.inflow.client;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.FormattedCharSequence;
/** Read-only wrapped records; journal clicks never execute commands. */
public final class ModerationEntryScreen extends FixedModerationScreen {
 private final Screen parent;private final JsonObject entry;private int page;private Button previous,next;
 private static final int X=32,Y=90,LINE=12,COUNT=24,WRAP=520;
 public ModerationEntryScreen(Screen parent,JsonObject entry){super(Component.literal("Запись журнала"));this.parent=parent;this.entry=entry.deepCopy();}
 private String value(String key){String value=entry.has(key)&&!entry.get(key).isJsonNull()?entry.get(key).getAsString():"—";return key.equals("result")?ModerationLabels.readable(value):value;}
 public static String actionTitle(JsonObject entry){
  String raw=entry.has("action")&&!entry.get("action").isJsonNull()?entry.get("action").getAsString():"—";
  int space=raw.indexOf(' ');String key=space<0?raw:raw.substring(0,space),suffix=space<0?"":raw.substring(space);
  String title=switch(key){case "teleport"->"Телепорт к игроку";case "teleport-here"->"Телепорт игрока к модератору";case "spectate"->"Начало наблюдения";case "spectate-return"->"Выход из наблюдения";case "staff-message"->"Сообщение игроку";case "check"->"Вызов на проверку";case "check-pass"->"Проверка пройдена";case "co-inspect"->"Инспектор CoreProtect";case "co-lookup"->"Поиск в CoreProtect";case "note"->"Заметка в деле";case "case-assign"->"Назначение дела";case "case-state"->"Изменение статуса дела";case "warn"->"Предупреждение · правило";case "mute"->"Мут · правило";case "ban"->"Бан · правило";case "punishment-conflict"->"Конфликт наказаний";case "evidence-saved"->"Сохранение вложений · правило";case "discord-evidence"->"Вложения отправлены в Discord";case "report-submit"->"Новая жалоба";case "report-claim"->"Жалоба взята в работу";case "report-release"->"Жалоба освобождена";case "report-close"->"Жалоба закрыта";case "animated-ban-request"->"Запрос бана с анимацией";case "animated-ban"->"Проверка результата бана";case "animated-ban-conflict"->"Конфликт бана с анимацией";default->key;};
  return ModerationLabels.readable(title+suffix);
 }
 private List<FormattedCharSequence> lines(){var lines=new ArrayList<FormattedCharSequence>();for(String item:List.of("Модератор: "+value("actor"),"Игрок: "+value("targetName"),"Действие: "+actionTitle(entry),"Команда: "+value("command"),"Результат: "+value("result"))){lines.addAll(font.split(Component.literal(item),WRAP));lines.add(FormattedCharSequence.EMPTY);}return lines;}
 private int pages(){return Math.max(1,(lines().size()+COUNT-1)/COUNT);}
 @Override protected void initMenu(){
  String link=value("result");if(link.matches("https://discord\\.com/channels/[0-9]+/[0-9]+/[0-9]+"))addRenderableWidget(ModerationButtons.builder(Component.literal("Открыть в Discord"),b->net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this,java.net.URI.create(link))).bounds(402,52,150,20).build());
  previous=addRenderableWidget(ModerationButtons.builder(Component.literal("← Назад"),b->page=Math.max(0,page-1)).bounds(X,392,100,20).build());
  next=addRenderableWidget(ModerationButtons.builder(Component.literal("Далее →"),b->page=Math.min(pages()-1,page+1)).bounds(452,392,100,20).build());
  addRenderableWidget(ModerationButtons.builder(Component.literal("Вернуться к списку"),b->onClose()).bounds(X,420,250,20).build());
  addRenderableWidget(ModerationButtons.builder(Component.literal("Копировать запись"),b->minecraft.keyboardHandler.setClipboard(entry.toString())).bounds(302,420,250,20).build());
 }
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
 @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float dt){
  String date=entry.has("time")?Instant.ofEpochMilli(entry.get("time").getAsLong()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")):"—";
  g.centeredText(font,"Подробности записи",width/2,28,0xFFF0EDF7);g.text(font,date,X,55,0xFFACB6C5,false);
  var lines=lines();page=Math.min(page,pages()-1);for(int i=page*COUNT;i<Math.min(lines.size(),(page+1)*COUNT);i++)g.text(font,lines.get(i),X,Y+i%COUNT*LINE,0xFFF0EDF7,false);
  previous.active=page>0;next.active=page+1<pages();g.centeredText(font,"Страница "+(page+1)+" / "+pages(),width/2,398,0xFFACB6C5);renderWidgets(g,mx,my,dt);
 }
}
