package com.wildfire.neoforge.appearance;

import com.wildfire.client.gui.WildfireButton;
import com.wildfire.client.gui.WildfireSlider;
import com.wildfire.client.appearance.BreastShape;
import com.wildfire.client.gui.screen.BaseWildfireScreen;
import com.wildfire.neoforge.appearance.ServerAppearanceSync.Cosmetics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import com.wildfire.neoforge.appearance.CosmeticLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Full-body, server-backed appearance editor. */
public final class WildfireBreastCustomizationScreen extends BaseWildfireScreen {
    private static final int WHITE = com.wildfire.gui.UiTheme.TEXT;
    private static final int MUTED = com.wildfire.gui.UiTheme.MUTED;
    private static final int STROKE = com.wildfire.gui.UiTheme.BORDER;
    private static final String[] SHIRTS = {"Нет", "Светлая", "Графит", "Слива"};
    private static final String[] PANTS = {"Нет", "Тёмные", "Серые", "Каштан"};
    private static final String[] HEADWEAR = {"Нет", "Кепка", "Нимб"};
    private static final String[] PETS = {"Нет", "ImZap", "Lisa_Mary", "Xle8 · Хлеб", "_Nekes_"};

    private final UUID sessionToken;
    private final boolean female;
    private final boolean figureEditable;
    private final float originalSize;
    private final BreastShape originalShape;
    private final Cosmetics originalCosmetics;
    private BreastShape shape;
    private int shirt, pants, headwear, pet, category;
    private int[] categories;
    private boolean submitted;
    private boolean initialized;
    private WildfireButton[] categoryButtons;
    private WildfireButton[] optionButtons;
    private WildfireSlider sizeSlider;
    private WildfireButton saveButton;
    private WildfireButton purchaseButton;
    private WildfireButton[] cosmeticTiles;
    private WildfireButton[] petFilters;
    private WildfireButton previousPage, nextPage;
    private int cosmeticPage, petFilter;
    private java.util.List<Integer> visibleCosmetics = java.util.List.of();
    private com.wildfire.gui.PlayerAssetsScreen assets;

    public WildfireBreastCustomizationScreen(@Nullable Screen parent, UUID playerUUID, UUID sessionToken,
                                             boolean female, boolean figureEditable, float initialSize, BreastShape initialShape, Cosmetics initialCosmetics) {
        super(Component.literal("Кастомизация"), parent, playerUUID);
        this.sessionToken = sessionToken;
        this.female = female;
        this.figureEditable = figureEditable;
        this.originalSize = Math.clamp(initialSize, 0f, 0.8f);
        this.originalShape = initialShape;
        this.originalCosmetics = initialCosmetics;
        this.shape = initialShape;
        this.shirt = initialCosmetics.shirt();
        this.pants = initialCosmetics.pants();
        this.headwear = initialCosmetics.headwear();
        this.pet = initialCosmetics.pet();
        this.category = female && figureEditable ? 0 : 1;
    }

    @Override
    protected void init() {
        super.init();
        var player = Objects.requireNonNull(getPlayer(), "server appearance profile");
        if (!initialized && female) player.breasts().bustSize().update(originalSize);
        initialized = true;
        ServerAppearanceSync.preview(playerUUID, new Cosmetics(shirt, pants, headwear, pet));
        int x = left(), y = top(), rail = 78;
        int preview = Math.max(116, panelWidth() * 36 / 100);
        int controlsX = x + rail + preview + 14;
        int controlsW = x + panelWidth() - controlsX - 12;
        if(assets==null)assets=new com.wildfire.gui.PlayerAssetsScreen(this,com.wildfire.neoforge.appearance.PrivateMessageInbox::requestAssetTicket);
        assets.mount(controlsX,y+34,controlsW,panelHeight()-78,button->addRenderableWidget(button));

        categories = female && figureEditable ? new int[]{0, 1, 3, 4, 5} : new int[]{1, 3, 4, 5};
        categoryButtons = new WildfireButton[categories.length];
        String[] labels = {"Фигура", "Одежда", "Лицо", "Головные", "Питомцы", "Скин/плащ"};
        for (int i = 0; i < categories.length; i++) {
            final int target = categories[i];
            categoryButtons[i] = cleanButton(labels[target], x + 10, y + 45 + i * 26, rail - 20, 21,
                () -> { category = target; cosmeticPage = 0; refreshControls(); });
        }

        sizeSlider = addSlider(builder -> builder
            .message(value -> Component.literal("Размер: " + Math.round(value * 100)))
            .position(controlsX, y + 78).size(controlsW, 22)
            .forConfig(player.breasts()::bustSize)
            .save(_ -> {}).step(0.01).mouseStep(0.001));

        optionButtons = new WildfireButton[4];
        optionButtons[0] = cleanButton("<", controlsX, y + 112, 25, 22, () -> changeShape(-1));
        optionButtons[1] = cleanButton(">", controlsX + controlsW - 25, y + 112, 25, 22, () -> changeShape(1));
        optionButtons[2] = cleanButton("", controlsX, y + 78, controlsW, 22, this::cyclePrimary);
        optionButtons[3] = cleanButton("", controlsX, y + 112, controlsW, 22, this::cycleSecondary);

        petFilters = new WildfireButton[4];
        String[] filterNames = {"Все", "Летающие", "Ходячие", "Парные"};
        for (int i = 0; i < petFilters.length; i++) {
            final int filter = i;
            petFilters[i] = cleanButton(filterNames[i], controlsX + i * controlsW / 4, y + 70,
                Math.max(20, controlsW / 4 - 2), 19,
                () -> { petFilter = filter; cosmeticPage = 0; refreshControls(); });
        }
        cosmeticTiles = new WildfireButton[6];
        for (int i = 0; i < cosmeticTiles.length; i++) {
            final int slot = i;
            int tileSize = Math.min(60, (controlsW - 8) / 3);
            int tileX = controlsX + (controlsW - (tileSize * 3 + 8)) / 2 + (i % 3) * (tileSize + 4);
            int tileY = y + 93 + (i / 3) * (tileSize + 4);
            cosmeticTiles[i] = addButton(builder -> builder.message(() -> Component.empty())
                .position(tileX, tileY).size(tileSize, tileSize)
                .onPress(_ -> selectCosmetic(slot))
                .renderer((button, graphics, mouseX, mouseY, delta) -> {
                    int index = cosmeticPage * cosmeticTiles.length + slot;
                    if (index >= visibleCosmetics.size()) return;
                    int id = visibleCosmetics.get(index);
                    boolean selected = (category == 3 ? headwear : pet) == id;
                    com.wildfire.gui.FlatButtons.plate(graphics,button.getX(),button.getY(),button.getWidth(),button.getHeight(),
                        true,selected||button.isHoveredOrFocused(),selected,com.wildfire.gui.UiTheme.ACCENT);
                    var entry = CosmeticCatalogClient.entry(id);
                    boolean relationship = entry != null && entry.kind().equals("pet_relationship");
                    var modelPreview = relationship ? null : CosmeticLayer.guiPreview(id, category == 3);
                    if (relationship && minecraft.player != null) {
                        var people = CosmeticLayer.relationshipGuiPreviews(minecraft.player.getUUID());
                        graphics.text(font, "♥", button.getX()+tileSize/2-4, button.getY()+9, 0xFFE8323E, false);
                        int artBottom = button.getBottom() - 15;
                        int areaWidth = button.getWidth() - 4;
                        for (int person = 0; person < people.size(); person++) {
                            int left = button.getX() + 2 + person * areaWidth / people.size();
                            int right = button.getX() + 2 + (person + 1) * areaWidth / people.size();
                            var figure = people.get(person);
                            graphics.skin(figure.model(), figure.texture(), 13f, 8f,
                                person == 0 ? -8f : person == people.size()-1 ? 8f : 0f, 0f,
                                left, button.getY()+6, right, artBottom+4);
                        }
                    } else if (modelPreview != null) {
                        int artBottom = button.getBottom() - 15;
                        // GuiGraphicsExtractor.skin takes scale before rotationX, rotationY and pivotY.
                        graphics.skin(modelPreview.model(), modelPreview.texture(), category == 3 ? 20f : 16f,
                            category == 3 ? 16f : 8f, category == 3 ? -28f : -10f, 0f,
                            button.getX()+4, button.getY()+6, button.getRight()-4, artBottom+4);
                    } else if (id != 0) {
                        graphics.text(font, "…", button.getX()+tileSize/2-4, button.getY()+17, MUTED, false);
                    }
                    String label = category == 3 ? cosmeticName(id, HEADWEAR) : cosmeticName(id, PETS);
                    drawScrollingString(graphics, Component.literal(label), button.getX()+2,
                        button.getBottom()-15, button.getRight()-2, button.getBottom()-2,
                        TextAlignment.CENTER, WHITE, false);
                }));
        }
        previousPage = cleanButton("←", controlsX, y + 223, 27, 19, () -> { cosmeticPage--; refreshControls(); });
        nextPage = cleanButton("→", controlsX + controlsW - 27, y + 223, 27, 19,
            () -> { cosmeticPage++; refreshControls(); });

        saveButton = cleanButton("Сохранить", x + panelWidth() - 104, y + panelHeight() - 33, 92, 22, () -> {
            if (lockedSelection()) { ModUpdate.cosmeticFeedback("Нужен доступ — donate.springplus.net", -1f, 10); return; }
            submitted = ServerAppearanceSync.submit(sessionToken, female ? player.breasts().bustSize().get() : originalSize, shape,
                originalCosmetics, new Cosmetics(shirt, pants, headwear, pet));
            if (!submitted) { ModUpdate.cosmeticFeedback("Не удалось отправить образ", -1f, 10); return; }
            ModUpdate.appearanceSavePending();
            //~ if >=26.2 'minecraft.setScreen' -> 'minecraft.gui.setScreen'
            minecraft.gui.setScreen(parent);
        });
        purchaseButton = addButton(builder -> builder.message(() -> Component.literal("Купить на сайте"))
            .position(controlsX + 30, y + 223).size(controlsW - 60, 19)
            .onPress(_ -> org.lwjgl.sdl.SDLMisc.SDL_OpenURL("https://donate.springplus.net"))
            .renderer((button, graphics, mouseX, mouseY, delta) -> {
                graphics.fill(button.getX(), button.getY(), button.getRight(), button.getBottom(),
                    button.isHoveredOrFocused() ? 0xDD65445F : 0xCC48364B);
                graphics.fill(button.getX(), button.getBottom() - 1, button.getRight(), button.getBottom(), 0xFFBB83B0);
                graphics.item(new ItemStack(lockedPet() ? Items.ALLAY_SPAWN_EGG : Items.LEATHER_HELMET),
                    button.getX() + 4, button.getY() + 4);
                drawScrollingString(graphics, button.getMessage(), button.getX() + 22,
                    button.getY(), button.getRight() - 3, button.getBottom(), TextAlignment.CENTER, WHITE, false);
            }));
        refreshControls();
    }

    private WildfireButton cleanButton(String label, int x, int y, int w, int h, Runnable action) {
        return addButton(builder -> builder.message(() -> Component.literal(label))
            .position(x, y).size(w, h).onPress(_ -> action.run())
            .renderer((button, graphics, mouseX, mouseY, delta) -> {
                com.wildfire.gui.FlatButtons.plate(graphics,button.getX(),button.getY(),button.getWidth(),button.getHeight(),
                    button.active,button.isHoveredOrFocused(),false,com.wildfire.gui.UiTheme.ACCENT);
                drawScrollingString(graphics, button.getMessage(), button.getX() + 3, button.getY(),
                    button.getRight() - 3, button.getBottom(), TextAlignment.CENTER, WHITE, false);
            }));
    }

    private void refreshControls() {
        if(assets!=null)assets.visible(category==5);
        for (int i = 0; i < categoryButtons.length; i++) categoryButtons[i].active = categories[i] != category;
        sizeSlider.visible = female && category == 0;
        optionButtons[0].visible = female && category == 0;
        optionButtons[1].visible = female && category == 0;
        optionButtons[2].visible = category == 1;
        optionButtons[3].visible = category == 1;
        boolean gallery = category == 3 || category == 4;
        if (gallery) visibleCosmetics = cosmeticChoices();
        int pages = Math.max(1, (visibleCosmetics.size() + cosmeticTiles.length - 1) / cosmeticTiles.length);
        cosmeticPage = Math.clamp(cosmeticPage, 0, pages - 1);
        for (int i = 0; i < cosmeticTiles.length; i++)
            cosmeticTiles[i].visible = gallery && cosmeticPage * cosmeticTiles.length + i < visibleCosmetics.size();
        for (int i = 0; i < petFilters.length; i++) {
            petFilters[i].visible = category == 4;
            petFilters[i].active = petFilter != i;
        }
        previousPage.visible = gallery && cosmeticPage > 0;
        nextPage.visible = gallery && cosmeticPage < pages - 1;
        String primary = switch(category) {
            case 1 -> "Футболка: " + SHIRTS[shirt];
            case 3 -> "Головной убор: " + cosmeticName(headwear,HEADWEAR);
            case 4 -> "Питомец: " + cosmeticName(pet,PETS);
            default -> "";
        };
        optionButtons[2].setMessage(Component.literal(primary));
        optionButtons[3].setMessage(Component.literal("Штаны: " + PANTS[pants]));
        saveButton.active = !lockedSelection();
        saveButton.visible=category!=5;
        purchaseButton.visible = lockedSelection();
        if (gallery && purchaseButton.visible) {
            previousPage.visible = false;
            nextPage.visible = false;
        }
    }

    private java.util.List<Integer> cosmeticChoices() {
        var choices = new java.util.ArrayList<Integer>();
        if (category == 3) {
            for (int i = 0; i < HEADWEAR.length; i++) choices.add(i);
        } else {
            choices.add(0);
            if (petFilter == 0 || petFilter == 1) for (int i = 1; i < PETS.length; i++) choices.add(i);
        }
        CosmeticCatalogClient.entries().stream()
            .filter(entry -> category == 3 ? entry.kind().equals("headwear") :
                switch (petFilter) {
                    case 1 -> entry.kind().equals("pet_flying");
                    case 2 -> entry.kind().equals("pet_walking");
                    case 3 -> entry.kind().equals("pet_relationship");
                    default -> entry.kind().startsWith("pet_");
                })
            .sorted(java.util.Comparator.comparingInt(CosmeticCatalogPackets.Entry::networkId))
            .forEach(entry -> { if (!choices.contains(entry.networkId())) choices.add(entry.networkId()); });
        return choices;
    }

    private void selectCosmetic(int slot) {
        int index = cosmeticPage * cosmeticTiles.length + slot;
        if (index >= visibleCosmetics.size()) return;
        if (category == 3) headwear = visibleCosmetics.get(index);
        else pet = visibleCosmetics.get(index);
        previewCosmetics();
    }

    private void changeShape(int direction) {
        BreastShape[] shapes = BreastShape.values();
        shape = shapes[Math.floorMod(shape.ordinal() + direction, shapes.length)];
        ServerAppearanceSync.preview(playerUUID, shape);
    }

    private void cyclePrimary() {
        if (category == 1) {
            shirt = (shirt + 1) % SHIRTS.length;
            previewCosmetics();
        } else if (category == 3) {
            headwear = nextCosmetic(headwear,HEADWEAR.length,false);
            previewCosmetics();
        } else if (category == 4) {
            pet = nextCosmetic(pet,PETS.length,true);
            previewCosmetics();
        }
    }

    private static String cosmeticName(int id,String[] defaults){
        if(defaults==HEADWEAR&&id>=0&&id<defaults.length)return defaults[id];
        var entry=CosmeticCatalogClient.entry(id);
        if(entry!=null&&(defaults==PETS?entry.kind().startsWith("pet_"):entry.kind().equals("headwear")))return entry.name();
        if(id>=0&&id<defaults.length)return defaults[id];
        return entry==null?"Нет":entry.name();
    }

    private static int nextCosmetic(int current,int defaults,boolean pet){
        var choices=new java.util.ArrayList<Integer>();
        choices.add(0);
        for(int i=1;i<defaults;i++)choices.add(i);
        CosmeticCatalogClient.entries().stream()
            .filter(entry->pet?entry.kind().startsWith("pet_"):entry.kind().equals("headwear"))
            .sorted(java.util.Comparator.comparingInt(CosmeticCatalogPackets.Entry::networkId))
            .forEach(entry->{if(!choices.contains(entry.networkId()))choices.add(entry.networkId());});
        int index=choices.indexOf(current);
        return choices.get((index+1)%choices.size());
    }

    private boolean lockedSelection(){return lockedPet() || lockedHeadwear();}
    private boolean lockedPet(){
        if(pet==0)return false;
        var entry=CosmeticCatalogClient.entry(pet);
        if(entry!=null)return !entry.owned();
        if(pet<5)return !CosmeticCatalogClient.staticPetOwned(pet);
        return entry==null||!entry.owned();
    }
    private boolean lockedHeadwear(){
        if(headwear<5)return false;
        var entry=CosmeticCatalogClient.entry(headwear);
        return entry==null||!entry.owned();
    }
    private String purchaseHint(){
        int id=lockedPet()?pet:lockedHeadwear()?headwear:0;
        if(id==0)return null;
        var entry=CosmeticCatalogClient.entry(id);
        return entry==null?"Нужно купить, чтобы сохранить" :
            entry.price()>0?"Нужно купить · Цена: "+entry.price():"Недоступно · Требуется право доступа";
    }

    private void cycleSecondary() {
        pants = (pants + 1) % PANTS.length;
        previewCosmetics();
    }

    private void previewCosmetics() {
        ServerAppearanceSync.preview(playerUUID, new Cosmetics(shirt, pants, headwear, pet));
        refreshControls();
        if (lockedSelection()) ModUpdate.cosmeticFeedback("Нужен доступ — кнопка покупки в меню", -1f, 9);
    }

    @Override
    public void removed() {
        if(assets!=null)assets.removed();
        if (!submitted) {
            var player = getPlayer();
            if (player != null && female) player.breasts().bustSize().update(originalSize);
            ServerAppearanceSync.preview(playerUUID, originalShape);
            ServerAppearanceSync.preview(playerUUID, originalCosmetics);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(graphics);
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        int stageX = x + 78, preview = Math.max(116, w * 36 / 100);
        com.wildfire.gui.RoundedPanels.fill(graphics,x,y,x+w,y+h,com.wildfire.gui.UiTheme.BACKGROUND,6);
        com.wildfire.gui.FlatButtons.joinedBackdrop(graphics,x+1,y+1,x+w-1,y+32,com.wildfire.gui.UiTheme.CHROME,5,true,false);
        graphics.fill(x+1,y+32,x+w-1,y+33,STROKE);
        com.wildfire.gui.FlatButtons.frame(graphics,x,y,x+w,y+h,com.wildfire.gui.UiTheme.ACCENT,6);
        com.wildfire.gui.RoundedPanels.fill(graphics,stageX,y+36,stageX+preview,y+h-42,com.wildfire.gui.UiTheme.CHROME,4);
        com.wildfire.gui.RoundedPanels.fill(graphics,stageX+preview+7,y+36,x+w-7,y+h-42,com.wildfire.gui.UiTheme.CHROME,4);
        graphics.fill(stageX-5,y+40,stageX-4,y+h-46,STROKE);
        if(assets!=null){
            assets.renderEmbedded(graphics,stageX+5,y+39,preview-10,h-86,mouseX,mouseY,delta);
        } else if (minecraft.player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                stageX + 5, y + 39, stageX + preview - 5, y + h - 47,
                Math.max(32, Math.min(74, (h - 90) * 2 / 5)),
                getEntityScale(minecraft.player, 0f), mouseX, mouseY, minecraft.player);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (getFocused() == previousPage || getFocused() == nextPage
            || optionButtons != null && (getFocused() == optionButtons[0] || getFocused() == optionButtons[1]))
            setFocused(null);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = left(), y = top();
        int controlsX = x + 78 + Math.max(116, panelWidth() * 36 / 100) + 14;
        String title="Персонализация";
        graphics.text(font,title,x+(panelWidth()-font.width(title))/2,y+12,WHITE,false);
        graphics.text(font, "ПЕРСОНАЖ", x + 87, y + 43, MUTED, false);
        graphics.text(font, switch(category) { case 0 -> "ФИГУРА"; case 1 -> "ОДЕЖДА"; case 3 -> "ГОЛОВНЫЕ УБОРЫ";case 5->"СКИН И ПЛАЩ"; default -> "ПИТОМЦЫ"; },
            controlsX, y + 49, MUTED, false);
        if (category == 0) {
            int center = controlsX + (x + panelWidth() - controlsX - 12) / 2;
            graphics.text(font, shape.label(), center - font.width(shape.label()) / 2, y + 119, WHITE, false);
        }
        graphics.text(font, "Поверните мышью", x + 87, y + panelHeight() - 30, MUTED, false);
        String hint=purchaseHint();
        if(hint!=null){
            int right=x+panelWidth()-12;
            graphics.fill(controlsX-4,y+146,right,y+170,0xCC382936);
            graphics.text(font,hint,controlsX,y+154,0xFFFFB7CB,false);
            graphics.text(font,"Примерка доступна до покупки",controlsX,y+176,MUTED,false);
        }
    }

    private int panelWidth() { return Math.min(width - 12, 500); }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){
        if(assets!=null&&assets.mouseClicked(event,doubleClick))return true;
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){
        if(assets!=null&&assets.mouseDragged(event,dx,dy))return true;
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){
        if(assets!=null&&assets.mouseReleased(event))return true;
        return super.mouseReleased(event);
    }
    private int panelHeight() { return Math.min(height - 12, 290); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
}
