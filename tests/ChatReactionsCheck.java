import com.wildfire.gui.ChatReactions;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.util.HashSet;

public final class ChatReactionsCheck {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        check(com.wildfire.gui.ChatLinks.valid("https://example.com/path?q=1")!=null,"HTTPS link accepted");
        check(com.wildfire.gui.ChatLinks.valid("www.example.com")!=null,"www uses HTTPS");
        check(com.wildfire.gui.ChatLinks.valid("javascript:alert(1)")==null,"script scheme rejected");
        check(com.wildfire.gui.ChatLinks.valid("file:///C:/secret")==null,"file scheme rejected");
        check(com.wildfire.gui.ChatLinks.valid("https://user:password@example.com")==null,"credential URLs rejected");
        String linkBody="Смотри https://example.com/path, и www.example.org!";
        var linked=com.wildfire.gui.ChatLinks.text(linkBody);
        check(linked.getString().equals(linkBody),"link styling preserves message punctuation");
        var urls=new java.util.ArrayList<String>();
        linked.visit((style,text)->{if(style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.OpenUrl url)urls.add(url.uri().toString());return java.util.Optional.empty();},Style.EMPTY);
        check(urls.equals(java.util.List.of("https://example.com/path","https://www.example.org")),"multiple links without trailing punctuation");
        check(ChatReactions.pickerTabActive(false, true), "stickers can be opened from emoji tab before catalog loads");
        check(!ChatReactions.pickerTabActive(false, false), "selected emoji tab is not clickable");
        check(ChatReactions.pickerTabActive(true, false), "emoji can be opened from stickers tab");
        check(!ChatReactions.pickerTabActive(true, true), "selected sticker tab is not clickable");
        check(ChatReactions.ALL.size() == 3944, "complete Unicode 17 catalog");
        check(ChatReactions.LEGACY.size() == 12, "old sticker tokens remain compatible");
        var ids = new HashSet<String>();
        Path root = Path.of(args[0]);
        var definition = com.google.gson.JsonParser.parseString(Files.readString(root.resolve("assets/appearance_sync/font/emoji.json")))
            .getAsJsonObject().getAsJsonArray("providers");
        check(definition.size() == 28, "12 legacy glyphs plus 16 compact atlases");
        for (int i = 0; i < ChatReactions.LEGACY.size(); i++) {
            var entry = ChatReactions.LEGACY.get(i);
            check(ids.add(entry.id()) && entry.id().matches("[a-z]+"), "unique safe asset id");
            check(ChatReactions.text(entry.alias()).getString().equals(entry.unicode()), "alias to emoji");
            check(ChatReactions.input(entry.alias()).getString().equals(entry.alias()), "input cursor offsets preserved for aliases");
            check(ChatReactions.sticker(entry.sticker()).equals(entry), "sticker round trip through text history");
            check(ChatReactions.sticker("text " + entry.sticker()) == null, "inline sticker token stays ordinary text");
            byte[] png = Files.readAllBytes(root.resolve("assets/appearance_sync/textures/gui/emoji/" + entry.id() + ".png"));
            check(png.length > 24 && ByteBuffer.wrap(png).getLong() == 0x89504e470d0a1a0aL, "PNG signature");
            check(ByteBuffer.wrap(png).getInt(16) == 72 && ByteBuffer.wrap(png).getInt(20) == 72, "known texture dimensions");
            check(definition.get(i).getAsJsonObject().getAsJsonArray("chars").get(0).getAsString().equals(entry.unicode()), "glyph codepoint mapped");
        }
        check(ChatReactions.text("Hello :smile: ❤\uFE0F :unknown:").getString().equals("Hello 😀 ❤ :unknown:"), "mixed Unicode, aliases, unknown text");
        check(ChatReactions.sticker("[Стикер: https://evil.invalid/image]") == null, "arbitrary image is never a sticker");
        final int[] emojiRuns = {0};
        ChatReactions.text("Hi 😀!").visit((style, text) -> {
            if (text.equals("😀")) {
                check(style.getFont() instanceof FontDescription.Resource resource && resource.id().toString().equals("appearance_sync:emoji"), "only supported emoji use custom font");
                emojiRuns[0]++;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        check(emojiRuns[0] == 1, "emoji style included");
        var catalog = com.google.gson.JsonParser.parseString(Files.readString(root.resolve("assets/appearance_sync/emoji-catalog.json"))).getAsJsonObject();
        var entries = catalog.getAsJsonArray("entries");
        check(entries.size() == 3944, "all fully qualified emoji imported");
        for (var element : entries) {
            var e=element.getAsJsonObject();
            String sequence=e.get("unicode").getAsString(), glyph=e.get("glyph").getAsString();
            check(ChatReactions.input(sequence).getString().equals(sequence), "UTF-16 input offsets preserved: "+sequence);
            String display=ChatReactions.text(sequence).getString();
            check(display.codePointCount(0,display.length())==1, "sequence renders as one glyph: "+sequence);
            int page=e.get("page").getAsInt(), cell=e.get("cell").getAsInt();
            String row=definition.get(page+12).getAsJsonObject().getAsJsonArray("chars").get(cell/16).getAsString();
            int start=row.offsetByCodePoints(0,cell%16), end=row.offsetByCodePoints(start,1);
            check(row.substring(start,end).equals(glyph), "atlas font mapping");
        }
        for (int i=0;i<16;i++) {
            byte[] atlas=Files.readAllBytes(root.resolve("assets/appearance_sync/textures/gui/emoji/atlas-"+i+".png"));
            check(ByteBuffer.wrap(atlas).getInt(16)==1152 && ByteBuffer.wrap(atlas).getInt(20)==1152, "full-resolution atlas dimensions");
        }
        for(var element:catalog.getAsJsonArray("aliases")) {
            String sequence=element.getAsJsonObject().get("unicode").getAsString();
            String display=ChatReactions.text(sequence).getString();
            check(display.codePointCount(0,display.length())==1,"qualified variant stays whole");
            check(ChatReactions.input(sequence).getString().equals(sequence),"variant input offsets preserved");
        }
        check(!com.wildfire.gui.EmojiCatalog.select(0,"сердце").isEmpty(),"Russian name search");
        int count=0;
        for(int group=1;group<=9;group++) {
            var selected=com.wildfire.gui.EmojiCatalog.select(group,"");
            check(!selected.isEmpty(),"category populated"); count+=selected.size();
        }
        check(count==3944,"categories cover full catalog exactly");
        check(com.wildfire.gui.EmojiCatalog.select(0,"no-such-emoji-abcxyz").isEmpty(),"empty search safe");
        check(Files.exists(root.resolve("TWEMOJI-NOTICE.txt")) && Files.exists(root.resolve("TWEMOJI-LICENSE-GRAPHICS.txt")), "graphics attribution bundled");
        System.out.println("Chat reactions checks passed: aliases, Unicode, styles, bounded stickers, assets, attribution");
    }
}
