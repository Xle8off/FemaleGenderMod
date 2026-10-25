package com.wildfire.gui;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** Offline, versioned Unicode catalog. Longest-match decoding keeps ZWJ families and flags intact. */
public final class EmojiCatalog {
    public static final List<String> GROUPS = List.of("Все", "Смайлы", "Люди", "Природа", "Еда", "Путешествия", "Занятия", "Предметы", "Символы", "Флаги");
    private record Glyph(ChatReactions.Reaction reaction, String display, int group, int page, int cell, String search) {}
    private static final class Node { final Map<Integer, Node> children = new HashMap<>(); Glyph glyph; }
    private static final Node ROOT = new Node();
    private static final Map<String, Glyph> BY_ID = new HashMap<>();
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("appearance_sync", "emoji"));
    private EmojiCatalog() {}
    static List<ChatReactions.Reaction> load(List<ChatReactions.Reaction> legacy) {
        try (var stream = EmojiCatalog.class.getResourceAsStream("/assets/appearance_sync/emoji-catalog.json")) {
            if (stream == null) throw new IllegalStateException("Missing bundled emoji catalog");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<ChatReactions.Reaction> all = new ArrayList<>(legacy);
            List<Glyph> glyphs = new ArrayList<>();
            for (var element : json.getAsJsonArray("entries")) {
                var e = element.getAsJsonObject();
                String unicode = e.get("unicode").getAsString();
                var reaction = new ChatReactions.Reaction(e.get("id").getAsString(), unicode, e.get("label").getAsString());
                var glyph = new Glyph(reaction, e.get("glyph").getAsString(), e.get("group").getAsInt(), e.get("page").getAsInt(), e.get("cell").getAsInt(), e.get("search").getAsString().toLowerCase(Locale.ROOT));
                glyphs.add(glyph); BY_ID.put(reaction.id(), glyph); insert(unicode, glyph);
                if (legacy.stream().noneMatch(l -> l.unicode().equals(unicode) || (l.unicode()+"\uFE0F").equals(unicode))) all.add(reaction);
            }
            for (var element : json.getAsJsonArray("aliases")) {
                var e = element.getAsJsonObject(); insert(e.get("unicode").getAsString(), glyphs.get(e.get("index").getAsInt()));
            }
            for (var l : legacy) {
                var glyph = glyphs.stream().filter(g -> g.reaction.unicode().equals(l.unicode()) || g.reaction.unicode().equals(l.unicode()+"\uFE0F")).findFirst().orElseThrow();
                // Preserve old text and sticker tokens, while keeping their familiar glyphs.
                var old = new Glyph(l, l.unicode(), glyph.group, -1, 0, glyph.search);
                BY_ID.put(l.id(), old); insert(l.unicode(), old); insert(l.unicode()+"\uFE0F", old);
            }
            return List.copyOf(all);
        } catch (Exception error) { throw new IllegalStateException("Invalid bundled emoji catalog", error); }
    }
    private static void insert(String sequence, Glyph glyph) {
        Node node=ROOT;
        for(int i=0;i<sequence.length();) { int cp=sequence.codePointAt(i); node=node.children.computeIfAbsent(cp,k->new Node()); i+=Character.charCount(cp); }
        node.glyph=glyph;
    }
    public static List<ChatReactions.Reaction> select(int category, String query) {
        String term=query.strip().toLowerCase(Locale.ROOT);
        return ChatReactions.ALL.stream().filter(e -> category==0 || BY_ID.get(e.id()).group==category-1)
            .filter(e -> term.isEmpty() || e.label().toLowerCase(Locale.ROOT).contains(term) || e.unicode().contains(term)
                || BY_ID.get(e.id()).search.contains(term)
                || GROUPS.get(BY_ID.get(e.id()).group+1).toLowerCase(Locale.ROOT).contains(term)).toList();
    }
    static Component text(String body, boolean input) {
        MutableComponent result=Component.empty(); StringBuilder plain=new StringBuilder();
        for(int i=0;i<body.length();) {
            Node node=ROOT; Glyph match=null; int end=i;
            for(int j=i;j<body.length();) {
                int cp=body.codePointAt(j); node=node.children.get(cp); if(node==null)break;
                j+=Character.charCount(cp); if(node.glyph!=null) { match=node.glyph; end=j; }
            }
            if(match==null) { int cp=body.codePointAt(i); plain.appendCodePoint(cp); i+=Character.charCount(cp); continue; }
            if(!plain.isEmpty()) { result.append(Component.literal(plain.toString())); plain.setLength(0); }
            if(input) {
                // Never collapse UTF-16 input: EditBox cursor/selection use the original offsets.
                String original=body.substring(i,end);
                result.append(match.display.equals(original) ? Component.literal(original).withStyle(s->s.withFont(FONT).withColor(0xFFFFFF)) : Component.literal(original));
            } else result.append(Component.literal(match.display).withStyle(s->s.withFont(FONT).withColor(0xFFFFFF)));
            i=end;
        }
        if(!plain.isEmpty())result.append(Component.literal(plain.toString()));
        return result;
    }
    static void draw(GuiGraphicsExtractor graphics, ChatReactions.Reaction reaction, int x, int y, int size) {
        Glyph g=BY_ID.get(reaction.id()); if(g==null || size<=0)return;
        String asset=g.page<0 ? reaction.id() : "atlas-"+g.page;
        int tile=72;
        int dimension=g.page<0 ? 72 : 1152;
        graphics.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("appearance_sync","textures/gui/emoji/"+asset+".png"),
            x,y,g.page<0?0f:(g.cell%16)*72f,g.page<0?0f:(g.cell/16)*72f,size,size,tile,tile,dimension,dimension);
    }
}
