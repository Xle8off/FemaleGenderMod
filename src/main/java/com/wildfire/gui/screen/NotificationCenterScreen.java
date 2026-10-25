package com.wildfire.gui.screen;
import com.wildfire.gui.RoundedPanels;
import com.wildfire.gui.UiTheme;
import com.wildfire.gui.DiscVoiceVolume;

import com.wildfire.client.config.CosmeticPreferences;
import com.wildfire.main.networking.appearance.PrivateMessageInbox;
import com.wildfire.main.networking.appearance.MusicClient;
import com.wildfire.main.networking.appearance.NotificationInbox;
import com.wildfire.main.networking.appearance.NotificationPreferenceSync;
import com.wildfire.main.networking.appearance.ModUpdate;
import com.wildfire.main.networking.appearance.SocialClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.PlayerSkin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Compact drawer opened from the bell in chat. */
public final class NotificationCenterScreen extends Screen implements com.wildfire.client.music.MenuPreviewSource {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final int[] ICON_COLORS = {UiTheme.ACCENT, UiTheme.ACCENT, UiTheme.ACCENT, UiTheme.ACCENT, UiTheme.ACCENT, 0xFF99BAFF};
    private final com.wildfire.gui.TownsPanel townsPanel = new com.wildfire.gui.TownsPanel();
    private final Screen parent;
    private final String initialPartner;
    private final Map<AbstractWidget, Integer> widgetX = new IdentityHashMap<>();
    private final List<Button> rowActions = new ArrayList<>();
    private final List<Button> messageActions = new ArrayList<>();
    private final List<IconTabButton> tabs = new ArrayList<>();
    private long openedAt;
    private long closingAt;
    private int playerClosingOffset;
    private int drawerWidth;
    private int page;
    private int tab;
    public int worldMenuCategory() { return Math.clamp(tab, 0, 3); }
    private EditBox friendName;
    private EditBox friendSearch;
    private int friendScroll;
    private String confirmRemoval = "";
    private long confirmRemovalUntil;

    private List<SocialClient.Row> friendRows() {
        String query = friendSearch == null ? "" : friendSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        return SocialClient.rows().stream().filter(row -> row.name().toLowerCase(java.util.Locale.ROOT).contains(query))
            .sorted(java.util.Comparator.comparingInt((SocialClient.Row row) -> "incoming".equals(row.kind()) ? 0
                : "friend".equals(row.kind()) ? ("in_game".equals(row.status()) ? 1 : 2) : 3)
                .thenComparing(SocialClient.Row::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private Button addFriend;
    private Button popupOnlyButton;
    private Button backToChats;
    private Button manageGroup;
    private String pendingGroupSend = "", pendingGroupBody = "", pendingGroupPartner = "";
    private long pendingGroupAt;
    private record Dialog(String partner,String title,boolean group,boolean online,int unread,long updatedAt) {}
    private List<Dialog> dialogs() {
        var rows = new ArrayList<Dialog>();
        for (var c : PrivateMessageInbox.conversations()) rows.add(new Dialog(c.partner(),c.partner(),false,c.online(),c.unread(),c.updatedAt()));
        for (var g : com.wildfire.client.music.GroupInbox.groups()) rows.add(new Dialog(com.wildfire.client.music.GroupInbox.key(g.id()),g.title(),true,false,g.unread(),g.updatedAt()));
        rows.sort(java.util.Comparator.comparingLong(Dialog::updatedAt).reversed().thenComparing(Dialog::partner));
        return rows;
    }
    private boolean groupConversation() { return com.wildfire.client.music.GroupInbox.isGroup(conversationPartner); }
    private String groupId() { return com.wildfire.client.music.GroupInbox.id(conversationPartner); }
    private List<String> groupFriends() { return SocialClient.rows().stream().filter(r -> "friend".equals(r.kind())).map(SocialClient.Row::name).toList(); }
    public void openGroupConversation(String key) {
        if (key.isEmpty()) { PrivateMessageInbox.close(); conversationPartner=null; updateWidgets(); }
        else openConversation(key);
    }
    private long sidebarTransitionAt;
    private Button backToNotifications;
    private net.minecraft.client.gui.components.MultiLineEditBox messageInput;
    private double animatedInputHeight=20;
    private long inputAnimationTime;
    private Button sendMessage;
    private Button attachImage;
    private String attachmentDraft;
    private Button reactionToggle, emojiTab, stickerTab;
    private final List<Button> reactionButtons = new ArrayList<>();
    private com.wildfire.gui.WebStickers.Sticker heldSticker;
    private Button heldStickerButton;
    private long stickerPressedAt;
    private boolean stickerPreviewShown;
    private double stickerPreviewOriginX,stickerPreviewOriginY;
    private com.wildfire.gui.WebStickers.Sticker returningSticker;
    private long stickerReleasedAt;
    private double stickerReturnFrom;
    private boolean returningFromPicker;
    private void updateStickerPreviewHover(double mouseX,double mouseY){
        if(heldSticker==null||tab!=2||closingAt!=0||
            System.nanoTime()-stickerPressedAt<1_000_000_000L)return;
        if(heldStickerButton!=null&&reactionPicker&&stickerPicker){
            var stickers=selectedStickers();
            for(int slot=0;slot<reactionButtons.size();slot++){
                var button=reactionButtons.get(slot);
                int index=reactionScrollRow*reactionColumns()+slot;
                if(button.visible&&button.active&&button.isMouseOver(mouseX,mouseY)&&index<stickers.size()){
                    heldSticker=stickers.get(index);heldStickerButton=button;
                    stickerPreviewOriginX=button.getX()+button.getWidth()/2d;
                    stickerPreviewOriginY=button.getY()+button.getHeight()/2d;
                    return;
                }
            }
        }else if(heldStickerButton==null){
            var sticker=conversationStickerAt(mouseX,mouseY);
            if(sticker!=null&&!sticker.id().equals(heldSticker.id())){
                heldSticker=sticker;stickerPreviewOriginX=mouseX;stickerPreviewOriginY=mouseY;
            }
        }
    }
    private void returnStickerPreview(){
        if(heldSticker!=null&&stickerPreviewShown){
            returningSticker=heldSticker;returningFromPicker=heldStickerButton!=null;
            stickerReleasedAt=System.nanoTime();
            double progress=Math.clamp((stickerReleasedAt-stickerPressedAt-1_000_000_000L)/220_000_000d,0,1);
            stickerReturnFrom=1-Math.pow(1-progress,3);
        }
    }
    private long lastMessageEdit, lastActivitySent;
    private String activityPartner="", activityState="idle";
    private boolean reactionPicker, stickerPicker;
    private EditBox emojiSearch;
    private final List<Button> emojiCategories = new ArrayList<>();
    private int emojiCategory;
    private String emojiFilterKey;
    private List<com.wildfire.gui.ChatReactions.Reaction> filteredEmojis = List.of();
    private int reactionScrollRow;
    private String selectedStickerPack = "";
    private int stickerPackScroll;
    private List<com.wildfire.gui.WebStickers.Sticker> stickerSelectionCatalog=List.of(),cachedStickerSelection=List.of(),packIdsCatalog=List.of();
    private List<String> cachedPackIds=List.of();
    private long packIdsRevision=-1;
    private String stickerSelectionKey="";
    private final List<Button> stickerPacks = new ArrayList<>();
    private List<com.wildfire.gui.WebStickers.Sticker> selectedStickers() {
        var entries=com.wildfire.gui.WebStickers.entries();
        String key=selectedStickerPack+":"+com.wildfire.gui.ReactionHistory.get().revision();
        if(entries==stickerSelectionCatalog&&key.equals(stickerSelectionKey))return cachedStickerSelection;
        stickerSelectionCatalog=entries;stickerSelectionKey=key;
        entries=entries.stream().filter(s->com.wildfire.gui.ReactionHistory.get().installed(s.id().split("/")[0])).toList();
        if(selectedStickerPack.startsWith("@")){
            var ids=com.wildfire.gui.ReactionHistory.get().ids("sticker",selectedStickerPack.equals("@favorites"));
            var byId=new java.util.HashMap<String,com.wildfire.gui.WebStickers.Sticker>();
            entries.forEach(s->byId.put(s.id(),s));
            cachedStickerSelection=ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        }else{
            cachedStickerSelection=entries.stream().filter(s -> s.id().startsWith(selectedStickerPack + "/")).toList();
        }
        return cachedStickerSelection;
    }
    private Button hurtSoundButton, cosmeticsButton, hudBellButton, hudTrackButton, notificationPopupsButton;
    private Button overheadModeButton,overheadMessagesButton,overheadCamerasButton;
    private Button campaignAdminButton,campaignVoteButton,playerAssetsButton,screenMenuButton;
    private Button hudPlayerButton;
    private Button vanillaChatButton;
    private int settingsCategory;
    private final List<Button> settingsCategories = new ArrayList<>();

    public static boolean hudPlayerVisible() {
        if (!com.wildfire.client.music.SpringPlusSession.enabled()) return false;
        return CosmeticPreferences.hudPlayer() && MusicClient.active()
            && Minecraft.getInstance().gui.screen() instanceof ChatScreen chat
            && com.wildfire.client.music.HudPlayerLayout.of(chat.width).fits(chat.height);
    }
    /** Interactive HUD controls while the chat provides a mouse cursor. */
    public static Button hudPlayerWidget(Screen owner) { return new HudPlayerWidget(owner); }
    private static final class HudPlayerWidget extends com.wildfire.client.music.ChatOverlayButton {
        private final Screen owner;
        private final NotificationCenterScreen player;
        HudPlayerWidget(Screen owner) {
            super(com.wildfire.client.music.HudPlayerLayout.of(owner.width).x(), 10,
                com.wildfire.client.music.HudPlayerLayout.of(owner.width).width(), 125,
                Component.literal("Музыкальный плеер"), button -> {});
            this.owner = owner;
            this.player = new NotificationCenterScreen(null);
            syncLayout();
        }
        private void syncLayout() {
            var layout = com.wildfire.client.music.HudPlayerLayout.of(owner.width);
            setX(layout.x()); setWidth(layout.width());
            player.width = owner.width; player.height = owner.height;
            player.drawerWidth = 0; player.openedAt = 0; player.closingAt = 0;
        }
        private boolean enabled() {
            return hudPlayerVisible() && Minecraft.getInstance().gui.screen() == owner
                && com.wildfire.client.music.HudPlayerLayout.of(owner.width).fits(owner.height);
        }
        private void restoreChatFocus() {
            if (owner.getFocused() == this) {
                owner.setDragging(false);
                for (var child : owner.children()) if (child instanceof net.minecraft.client.gui.components.EditBox input) {
                    owner.setFocused(input); break;
                }
            }
        }
        private void cancelDrag() {
            player.draggingMusicSeek = player.draggingRecordVolume = false;
            restoreChatFocus();
        }
        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            syncLayout();
            if (!enabled() || !super.isMouseOver(mouseX, mouseY)) return false;
            return player.onRecordVolumeSlider(mouseX, mouseY) || player.onMusicSeek(mouseX, mouseY)
                || MusicClient.personalActive() && com.wildfire.client.music.MusicUiButton.hit(mouseX, mouseY, getX() + getWidth() / 2) >= 0;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            syncLayout();
            // Button requests its cursor after this hook, using the hover flag set by AbstractWidget.
            isHovered = isHovered && isMouseOver(mouseX, mouseY);
            if (!enabled()) { cancelDrag(); return; }
            player.renderPlayer(graphics, mouseX, mouseY);
        }
        @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (click.button() != 0 || !isMouseOver(click.x(), click.y())) return false;
            if (player.onRecordVolumeSlider(click.x(), click.y())) {
                player.draggingRecordVolume = true; player.updateRecordVolume(click.x());
            } else if (player.onMusicSeek(click.x(), click.y())) {
                player.draggingMusicSeek = true;
                player.musicSeekTrack = MusicClient.position() + "\u0000" + MusicClient.title();
                player.previewMusicSeek(click.x());
            } else if (MusicClient.personalActive()) {
                int index = com.wildfire.client.music.MusicUiButton.hit(click.x(), click.y(), getX() + getWidth() / 2);
                if (index >= 0) MusicClient.control(index);
            }
            if (player.draggingRecordVolume || player.draggingMusicSeek) {
                owner.setFocused(this);
                owner.setDragging(true);
            }
            return true;
        }
        @Override public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
            syncLayout();
            if (!enabled()) { cancelDrag(); return false; }
            if (!player.draggingMusicSeek && !player.draggingRecordVolume) return false;
            return player.mouseDragged(click, dx, dy);
        }
        @Override public boolean mouseReleased(MouseButtonEvent click) {
            syncLayout();
            if (!enabled()) { cancelDrag(); return false; }
            if (!player.draggingMusicSeek && !player.draggingRecordVolume) return false;
            boolean handled = player.mouseReleased(click);
            restoreChatFocus();
            return handled;
        }
    }
    private Button listeningButton, ownListeningButton, shareListeningButton, discordPresenceButton;
    private Button musicPriorityButton;
    private Button screensEnabledButton;
    private String conversationPartner;
    private int conversationScroll;
    private int conversationSidebarScroll;
    private int tabBeforeChats = 0;
    private NotificationInbox.Entry selectedNotice;
    private int noticeScroll;
    private int notificationScroll;
    private NotificationInbox.Entry notificationAnchor;
    private int notificationAnchorInset;
    private long lastContactsRefresh;
    private boolean draggingRecordVolume;
    private static long lastSkinCapture;

    public static void cacheOnlineSkins() {
        long now = System.currentTimeMillis();
        if (now - lastSkinCapture < 5_000L) return;
        lastSkinCapture = now;
        // Capture local metadata only. Never fetch skins for the whole TAB from the HUD.
        com.wildfire.main.networking.appearance.GenderedNotifications.capture();
    }

    public NotificationCenterScreen(Screen parent) {
        this(parent, null);
    }

    public NotificationCenterScreen(Screen parent, String initialPartner) {
        super(Component.literal("SpringPlus"));
        this.parent = parent;
        this.initialPartner = initialPartner;
    }

    public void openNotification(String target) {
        String[] parts=target.split("\u0000",3);
        if(parts.length!=3)return;
        selectedNotice=NotificationInbox.entries().stream().filter(e->e.category().equals(parts[0])&&e.title().equals(parts[1])&&e.message().equals(parts[2])).findFirst().orElse(null);
    }
    @Override protected void init() {
        widgetX.clear(); tabs.clear(); rowActions.clear(); messageActions.clear();
        openedAt = System.currentTimeMillis();
        drawerWidth = Math.min(340, width);
        cameraButton=addRenderableWidget(new com.wildfire.gui.CameraButton(this,2,height-35));
        int content = contentLeft();
        int available = contentWidth();
        ItemStack[] icons = {
            new ItemStack(Items.BELL), new ItemStack(Items.PLAYER_HEAD),
            new ItemStack(Items.WRITABLE_BOOK), new ItemStack(Items.COMPARATOR), new ItemStack(Items.MUSIC_DISC_13), new ItemStack(Items.BRICKS)
        };
        String[] hints = {"Уведомления", "Друзья", "Личные сообщения", "Настройки", "Музыка", "Города"};
        int sidebarPitch=Math.min(21,Math.max(16,(height-88)/9));
        for (int i = 0; i < icons.length; i++) {
            IconTabButton button = new IconTabButton(i, icons[i], 2, 40 + i * sidebarPitch, 18, 18);
            button.setTooltip(Tooltip.create(Component.literal(hints[i])));
            tabs.add(button);
            addRenderableWidget(button);
        }
        popupOnlyButton = com.wildfire.gui.SettingsControls.toggle("Дублировать в чат", 0, () -> !CosmeticPreferences.popupOnly(), button -> {
            CosmeticPreferences.togglePopupOnly();
            NotificationPreferenceSync.syncNow();
            updateWidgets();
        }).bounds(content, 200, available, 22).build();
        addRenderableWidget(popupOnlyButton);
        campaignAdminButton=addRenderableWidget(new com.wildfire.gui.CampaignTabButton(this,true,2,40+6*sidebarPitch));
        campaignVoteButton=addRenderableWidget(new com.wildfire.gui.CampaignTabButton(this,false,2,40+7*sidebarPitch));
        screenMenuButton=addRenderableWidget(new com.wildfire.gui.ScreenMenuButton(this,2,40+8*sidebarPitch));
        notificationPopupsButton=com.wildfire.gui.SettingsControls.toggle("Всплывающие уведомления",0,
            CosmeticPreferences::notificationPopups,b->{CosmeticPreferences.toggleNotificationPopups();updateWidgets();})
            .bounds(content,172,available,22).build();
        addRenderableWidget(notificationPopupsButton);
        overheadModeButton=com.wildfire.gui.FlatButtons.builder(Component.literal("Режим над головой"),b->{
            CosmeticPreferences.cycleOverheadMode();
            if(CosmeticPreferences.overheadMode()!=com.wildfire.client.overhead.OverheadMedia.Mode.CAMERA&&com.wildfire.client.overhead.CameraClient.capture()!=null)com.wildfire.client.overhead.CameraClient.stop();
            updateWidgets();
        }).bounds(content,120,available,22).build();
        overheadMessagesButton=com.wildfire.gui.SettingsControls.toggle("Сообщения над головой",0,CosmeticPreferences::overheadMessages,b->{CosmeticPreferences.toggleOverheadMessages();updateWidgets();}).bounds(content,144,available,22).build();
        overheadCamerasButton=com.wildfire.gui.SettingsControls.toggle("Показывать чужие камеры",0,CosmeticPreferences::overheadCameras,b->{CosmeticPreferences.toggleOverheadCameras();updateWidgets();}).bounds(content,168,available,22).build();
        addRenderableWidget(overheadModeButton);addRenderableWidget(overheadMessagesButton);addRenderableWidget(overheadCamerasButton);
        hurtSoundButton = com.wildfire.gui.SettingsControls.toggle("Звук урона мода", 1, CosmeticPreferences::modHurtSound, button -> {
            CosmeticPreferences.toggleHurtSound(); updateWidgets();
        }).bounds(content, 88, available, 22).build();
        cosmeticsButton = com.wildfire.gui.SettingsControls.toggle("Показывать косметику", 1, CosmeticPreferences::cosmeticsVisible, button -> {
            CosmeticPreferences.toggleCosmetics(); updateWidgets();
        }).bounds(content, 116, available, 22).build();
        hudBellButton = com.wildfire.gui.SettingsControls.toggle("Колокольчик вне чата", 0, CosmeticPreferences::hudBell, button -> {
            CosmeticPreferences.toggleHudBell(); updateWidgets();
        }).bounds(content, 144, available, 22).build();
        hudTrackButton = com.wildfire.gui.SettingsControls.toggle("Строка трека", 2, CosmeticPreferences::hudTrack, button -> {
            CosmeticPreferences.toggleHudTrack(); updateWidgets();
        }).bounds(content, 172, available, 22).build();
        addRenderableWidget(hurtSoundButton);
        addRenderableWidget(cosmeticsButton);
        addRenderableWidget(hudBellButton);
        addRenderableWidget(hudTrackButton);
        hudPlayerButton = com.wildfire.gui.SettingsControls.toggle("Плеер при открытом чате", 2, CosmeticPreferences::hudPlayer, button -> {
            CosmeticPreferences.toggleHudPlayer(); updateWidgets();
        }).bounds(content, 60, available, 22).build();
        addRenderableWidget(hudPlayerButton);
        vanillaChatButton=addRenderableWidget(com.wildfire.gui.SettingsControls.toggle("Ванильный вид чата",0,
            ()->com.wildfire.gui.PublicChatState.filters().vanillaChat(),button->{
                com.wildfire.gui.PublicChatState.filters().toggleVanillaChat();updateWidgets();
            }).bounds(content,200,available,22).build());
        settingsCategories.clear();
        String[] categoryNames = {"События", "Вид", "Музыка"};
        for (int index = 0; index < categoryNames.length; index++) {
            final int category = index;
            int categoryWidth = Math.max(1, (available - 4) / 3);
            Button categoryButton = com.wildfire.gui.SettingsControls.category(categoryNames[index], index == 0 ? 0 : index == 1 ? 1 : 2, button -> {
                settingsCategory = category; updateWidgets();
            }).bounds(content + index * (categoryWidth + 2), 66, categoryWidth, 22).build();
            settingsCategories.add(categoryButton); addRenderableWidget(categoryButton);
        }
        screensEnabledButton=addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("Экраны"),b->{com.wildfire.client.screens.ScreenQuality.toggleEnabled();if(!com.wildfire.client.screens.ScreenQuality.enabled())com.wildfire.client.screens.BrowserRuntime.stop();updateWidgets();}).bounds(content,284,available,22).build());
        musicPriorityButton=addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("Приоритет музыки"),b->{com.wildfire.client.music.AudioPreferences.togglePriority();updateWidgets();}).bounds(content,284,available,22).build());
        listeningButton = addRenderableWidget(com.wildfire.gui.SettingsControls.toggle("Музыка над игроками", 2, CosmeticPreferences::musicLabels, b -> { CosmeticPreferences.toggleMusicLabels(); updateWidgets(); }).bounds(content, 200, available, 22).build());
        ownListeningButton = addRenderableWidget(com.wildfire.gui.SettingsControls.toggle("Своя музыка в F5", 2, CosmeticPreferences::ownMusicLabel, b -> { CosmeticPreferences.toggleOwnMusicLabel(); updateWidgets(); }).bounds(content, 228, available, 22).build());
        shareListeningButton = addRenderableWidget(com.wildfire.gui.SettingsControls.toggle("Делиться треком", 2, CosmeticPreferences::shareListening, b -> { CosmeticPreferences.toggleShareListening(); updateWidgets(); }).bounds(content, 256, available, 22).build());
        discordPresenceButton = addRenderableWidget(com.wildfire.gui.SettingsControls.category("Discord Rich Presence",0,b->minecraft.gui.setScreen(new com.wildfire.gui.DiscordPresenceScreen(this))).bounds(content,98,available,22).build());
        friendSearch = new EditBox(font, content, 64, available, 18, Component.literal("Поиск друзей"));
        friendSearch.setHint(Component.literal("Поиск по нику"));
        friendSearch.setMaxLength(16);
        friendSearch.setResponder(value -> { friendScroll = 0; confirmRemoval = ""; });
        addRenderableWidget(friendSearch);
        friendName = new EditBox(font, content, height - 65, available - 94, 20, Component.literal("Ник друга"));
        friendName.setMaxLength(16);
        friendName.setHint(Component.literal("Ник игрока"));
        addRenderableWidget(friendName);
        addFriend = com.wildfire.gui.FlatButtons.builder(Component.literal("Добавить"), button -> {
            String target = friendName.getValue().trim();
            if (target.matches("[A-Za-z0-9_]{1,16}")) {
                SocialClient.action("add", target);
                friendName.setValue("");
            }
        }).bounds(content + available - 90, height - 65, 90, 20).build();
        addRenderableWidget(addFriend);
        for (int i = 0; i < friendPageSize(); i++) {
            final int row = i;
            int y = 87 + i * 38;
            Button message = new FriendIconButton(row, true, content + available - 48, y + 5, button -> {
                int index = friendScroll / 38 + row;
                var rows = friendRows();
                if (index >= rows.size()) return;
                var entry = rows.get(index);
                if ("friend".equals(entry.kind()))
                    openConversation(entry.name());
                else if ("incoming".equals(entry.kind())) SocialClient.action("reject", entry.name());
            });
            messageActions.add(message);
            addRenderableWidget(message);
            Button action = new FriendIconButton(row, false, content + available - 22, y + 5, button -> friendAction(row));
            rowActions.add(action);
            addRenderableWidget(action);
        }
        backToChats = com.wildfire.gui.FlatButtons.builder(Component.literal("← Назад"), button -> {
            PrivateMessageInbox.close(); conversationPartner = null; conversationScroll = 0;
        }).bounds(content, 42, 75, 20).build();
        addRenderableWidget(backToChats);
        manageGroup = com.wildfire.gui.FlatButtons.builder(Component.literal("⋮"), button -> minecraft.gui.setScreen(
            new com.wildfire.client.music.GroupEditorScreen(this,groupId(),groupFriends(),this::openGroupConversation)))
            .bounds(content + available - 21,42,20,20).build();
        manageGroup.setTooltip(Tooltip.create(Component.literal("Участники и настройки группы"))); addRenderableWidget(manageGroup);
        backToNotifications = com.wildfire.gui.FlatButtons.builder(Component.literal("← Назад"), button -> {
            selectedNotice = null; noticeScroll = 0;
        }).bounds(content, 42, 75, 20).build();
        addRenderableWidget(backToNotifications);
        reactionButtons.clear();
        messageInput = net.minecraft.client.gui.components.MultiLineEditBox.builder().setX(content).setY(height-70)
            .setPlaceholder(Component.literal("Написать сообщение")).setTextColor(UiTheme.TEXT).setShowDecorations(false).setShowBackground(false)
            .build(font, Math.max(20,available-75),20,Component.literal("Сообщение"));
        messageInput.setCharacterLimit(256);
        com.wildfire.gui.FormattedChatInput.register(messageInput);
        messageInput.setValueListener(value -> {
            if (messageInput.isFocused()) lastMessageEdit = value.isBlank() ? 0 : System.currentTimeMillis();
        });
        addRenderableWidget(messageInput);
        if(attachmentDraft!=null){messageInput.setValue(attachmentDraft);attachmentDraft=null;}
        sendMessage = new com.wildfire.gui.ChatActionButton(2,content+available-22,height-70,button->sendPrivateMessage());
        addRenderableWidget(sendMessage);
        attachImage=new com.wildfire.gui.ChatActionButton(0,content+available-72,height-70,b->chooseChatImage(false));
        attachImage.setTooltip(Tooltip.create(Component.literal("Изображение · Ctrl+V из буфера")));
        addRenderableWidget(attachImage);
        reactionToggle = new com.wildfire.gui.ChatActionButton(1,content+available-47,height-70,button -> {
            reactionPicker = !reactionPicker;
            if (reactionPicker) com.wildfire.gui.WebStickers.refresh();
            updateWidgets();
        });
        reactionToggle.setTooltip(Tooltip.create(Component.literal("Эмодзи и стикеры")));
        addRenderableWidget(reactionToggle);
        int columns = reactionColumns();
        int pickerTop = reactionPickerTop();
        emojiTab = new ReactionTabButton(false, content, pickerTop + 4, Math.max(20, available / 2 - 2), button -> { stickerPicker = false; reactionScrollRow = 0; updateWidgets(); });
        stickerTab = new ReactionTabButton(true, content + available / 2 + 2, pickerTop + 4, Math.max(20, available / 2 - 2), button -> { stickerPicker = true; reactionScrollRow = 0; com.wildfire.gui.WebStickers.refresh(); updateWidgets(); });
        addRenderableWidget(emojiTab); addRenderableWidget(stickerTab);
        emojiSearch = new com.wildfire.gui.ModSearchBox(font, content, pickerTop + 27, available, 18, Component.literal("Поиск эмодзи"));
        emojiSearch.setMaxLength(80);
        emojiSearch.setHint(Component.literal("Поиск: emoji, heart, флаги…"));
        emojiSearch.setResponder(value -> { reactionScrollRow = 0; updateWidgets(); });
        addRenderableWidget(emojiSearch);
        emojiCategories.clear();
        String[] categoryIcons = {"◷", "★", "🙂", "😀", "👋", "🐻", "🍎", "🚗", "⚽", "💡", "🔣", "🏳️"};
        for (int i = 0; i < categoryIcons.length; i++) {
            int category = i - 2;
            int pitch=Math.min(24,Math.max(1,available/categoryIcons.length));
            int left = content + (available-(pitch*categoryIcons.length-2))/2 + i*pitch;
            int right = left + pitch-1;
            Button.OnPress categoryAction = button -> {
                emojiCategory = category; reactionScrollRow = 0; updateWidgets();
            };
            var categoryButton = i<2
                ? new HistoryCategoryButton(i==1,left,height-104,Math.max(1,right-left-1),categoryAction)
                : com.wildfire.gui.FlatButtons.builder(com.wildfire.gui.ChatReactions.text(categoryIcons[i]),categoryAction).bounds(left,height-104,Math.max(1,right-left-1),20).build();
            categoryButton.setTooltip(Tooltip.create(Component.literal(i==0?"Недавние":i==1?"Избранное":com.wildfire.gui.EmojiCatalog.GROUPS.get(i-2))));
            emojiCategories.add(categoryButton); addRenderableWidget(categoryButton);
        }
        int cell = Math.max(20, available / columns);
        for (int i = 0; i < Math.max(reactionSlots(false), reactionSlots(true)); i++) {
            int slot = i;
            var choice = new ReactionChoiceButton(slot, content + i % columns * cell, pickerTop + 49 + i / columns * 24, cell - 2, button -> {
                int index = reactionScrollRow * reactionColumns() + slot;
                if (stickerPicker) {
                    var stickers = selectedStickers();
                    if (index >= stickers.size() || !sendPrivateBody(stickers.get(index).message())) return;
                    com.wildfire.gui.ReactionHistory.get().used("sticker",stickers.get(index).id());
                } else {
                    if (index >= selectedEmojis().size()) return;
                    setFocused(messageInput);
                    selectedEmojis().get(index).unicode().codePoints().forEach(cp->messageInput.charTyped(new net.minecraft.client.input.CharacterEvent(cp)));
                    com.wildfire.gui.ReactionHistory.get().used("emoji",selectedEmojis().get(index).id());
                }
                reactionPicker = false;
                setFocused(messageInput);
                updateWidgets();
            });
            reactionButtons.add(choice); addRenderableWidget(choice);
        }
        stickerPacks.clear();
        for (int i = 0; i < Math.max(3,available/28); i++) {
            int slot = i;
            var pack = new StickerPackButton(slot, content + i * 28, height - 104, button -> {
                var packs = stickerPackIds();
                if (stickerPackIndex(slot) < packs.size()) {
                    String id=packs.get(stickerPackIndex(slot));
                    if(id.equals("@shop")){attachmentDraft=messageInput.getValue();minecraft.gui.setScreen(new com.wildfire.gui.StickerShopScreen(this));}
                    else {selectedStickerPack=id;reactionScrollRow=0;updateWidgets();}
                }
            });
            stickerPacks.add(pack); addRenderableWidget(pack);
        }
        addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("✕"), button -> onClose())
            .bounds(drawerWidth - 28, 10, 19, 19).build());
        for (var child : children()) if (child instanceof AbstractWidget widget) widgetX.put(widget, widget.getX());
        SocialClient.request();
        PrivateMessageInbox.requestContacts();
        com.wildfire.client.music.GroupInbox.refresh();
        updateWidgets();
        slideWidgets();
        if (initialPartner != null) openConversation(initialPartner);
    }

    private void setTab(int next) {
        reactionPicker = false;
        if (next == 5) {
            minecraft.gui.setScreen(new com.wildfire.gui.TownsScreen(this));
            return;
        }
        if (next == 4) {
            minecraft.gui.setScreen(new com.wildfire.client.music.MusicBrowserScreen(this));
            return;
        }
        if ((next == 2) != (tab == 2)) sidebarTransitionAt = System.currentTimeMillis();
        if (next == 2 && tab != 2) tabBeforeChats = tab;
        PrivateMessageInbox.close();
        conversationPartner = null;
        conversationScroll = 0;
        selectedNotice = null;
        noticeScroll = 0;
        tab = next;
        notificationScroll = 0;
        notificationAnchor = null;
        conversationSidebarScroll = 0;
        page = 0;
        if (tab == 1) SocialClient.request();
        if (tab == 5) townsPanel.open();
        if (tab == 2) { PrivateMessageInbox.requestContacts(); com.wildfire.client.music.GroupInbox.refresh(); SocialClient.request(); }
        updateWidgets();
    }

    private void openConversation(String partner) {
        receiptReveal.clear();
        reactionPicker = false;
        boolean group = com.wildfire.client.music.GroupInbox.isGroup(partner);
        if (group ? com.wildfire.client.music.GroupInbox.group(com.wildfire.client.music.GroupInbox.id(partner)) == null : !partner.matches("[A-Za-z0-9_]{1,16}")) return;
        if (tab != 2) sidebarTransitionAt = System.currentTimeMillis();
        if (tab != 2) tabBeforeChats = tab;
        tab = 2;
        page = 0;
        conversationPartner = partner;
        conversationScroll = 0;
        if (group) {
            PrivateMessageInbox.close(); com.wildfire.client.music.GroupInbox.open(groupId());
        } else {
            PrivateMessageInbox.open(partner); PrivateMessageInbox.requestHistory(partner); NotificationInbox.privateRead(partner);
        }
        if (messageInput != null) messageInput.setValue("");
        updateWidgets();
    }

    private void sendPrivateMessage() {
        if (conversationPartner == null || messageInput == null || minecraft.player == null) return;
        String body = messageInput.getValue().replaceAll("[\\r\\n]+"," ").trim();
        if (body.isEmpty()) return;
        if (!sendPrivateBody(body)) return;
        messageInput.setValue("");
        reactionPicker = false;
    }
    private void chooseChatImage(boolean paste){
        if(conversationPartner==null||closingAt!=0)return;
        String target=conversationPartner;
        attachmentDraft=messageInput==null?"":messageInput.getValue();
        com.wildfire.gui.ChatImages.choose(this,paste,PrivateMessageInbox::requestImageTicket,body->{
            if(target.equals(conversationPartner)&&!sendPrivateBody(body))ModUpdate.cosmeticFeedback("Не удалось отправить изображение", -1f, 7);
        });
    }
    private boolean sendPrivateBody(String body) {
        if (conversationPartner == null || minecraft.player == null || minecraft.getConnection() == null
            || body.isBlank() || body.length() > 256) return false;
        if (groupConversation()) {
            if (!pendingGroupSend.isEmpty()) return false;
            pendingGroupSend = com.wildfire.client.music.GroupInbox.request("send", Map.of("group",groupId(),"body",body));
            if (pendingGroupSend.isEmpty()) return false;
            pendingGroupBody = body; pendingGroupPartner = conversationPartner; pendingGroupAt = System.currentTimeMillis(); conversationScroll = 0; return true;
        }
        minecraft.player.connection.sendCommand("msg " + conversationPartner + " " + body);
        PrivateMessageInbox.receive(conversationPartner, true, body);
        conversationScroll = 0;
        return true;
    }

    private void friendAction(int row) {
        var rows = friendRows();
        int index = friendScroll / 38 + row;
        if (index >= rows.size()) return;
        var entry = rows.get(index);
        if ("friend".equals(entry.kind())) {
            if (!entry.name().equalsIgnoreCase(confirmRemoval) || System.currentTimeMillis() > confirmRemovalUntil) {
                confirmRemoval = entry.name();
                confirmRemovalUntil = System.currentTimeMillis() + 5000;
                return;
            }
            confirmRemoval = "";
        }
        SocialClient.action(switch (entry.kind()) {
            case "incoming" -> "add";
            case "outgoing" -> "cancel";
            default -> "remove";
        }, entry.name());
    }

    private int friendPageSize() { return com.wildfire.gui.FriendListLayout.create(height,0,0).slots(); }
    private int friendMaxScroll(int count) { return com.wildfire.gui.FriendListLayout.create(height,count,friendScroll).maxOffset(); }
    private int notificationPageSize() { return Math.max(1, Math.min(6, (height - 150) / 38)); }
    private NoticeListLayout notificationLayout(List<NotificationInbox.Entry> entries) {
        if(notificationScroll>0&&notificationAnchor!=null){
            int index=entries.indexOf(notificationAnchor);
            if(index>=0)notificationScroll=index*NoticeListLayout.PITCH+notificationAnchorInset;
        }
        var layout=NoticeListLayout.create(height,entries.size(),notificationScroll);
        notificationScroll=layout.offset();
        notificationAnchor=layout.first()<entries.size()?entries.get(layout.first()):null;
        notificationAnchorInset=layout.offset()%NoticeListLayout.PITCH;
        return layout;
    }
    private int contentLeft() { return 31; }
    private int contentWidth() { return drawerWidth - 43; }
    /** Header anchors for the Fabric InFlow adapter; includes the native drawer animation. */
    public int inflowControlX(boolean wheel) { return slideOffset() + (wheel ? drawerWidth - 24 : 2); }
    public boolean inflowControlsVisible() { return closingAt == 0 && drawerWidth >= 220; }
    private int sidebarSlots() { return Math.max(1, (height - 109) / 21); }
    private List<String> friendSuggestions() {
        if (tab != 1 || friendName == null || !friendName.isFocused()) return List.of();
        String query = friendName.getValue().trim();
        if (query.isEmpty()) return List.of();
        var names = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (minecraft.player != null) minecraft.player.connection.getListedOnlinePlayers()
            .forEach(info -> names.add(info.getProfile().name()));
        SocialClient.rows().forEach(row -> names.add(row.name()));
        String self = minecraft.player == null ? "" : minecraft.player.getName().getString();
        return names.stream().filter(name -> name.matches("[A-Za-z0-9_]{1,16}")
            && !name.equalsIgnoreCase(self) && !name.equalsIgnoreCase(query)
            && name.regionMatches(true, 0, query, 0, query.length())).limit(3).toList();
    }

    private void renderFriendSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var suggestions = friendSuggestions();
        int x = contentLeft() + slideOffset(), width = contentWidth() - 94;
        for (int i = 0; i < suggestions.size(); i++) {
            int y = friendName.getY() - 18 * (suggestions.size() - i);
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 18;
            RoundedPanels.fill(graphics, x, y, x + width, y + 18, hovered ? 0xFF485468 : 0xFF27313C);
            graphics.fill(x, y + 17, x + width, y + 18, 0xFF596779);
            graphics.text(font, suggestions.get(i), x + 5, y + 5, 0xFFE8EDF3, false);
        }
    }
    private int slideOffset() {
        if (closingAt != 0) {
            double t = Math.min(1.0, (System.currentTimeMillis() - closingAt) / 220.0);
            return -(int) Math.round(drawerWidth * t * t);
        }
        double t = Math.min(1.0, (System.currentTimeMillis() - openedAt) / 220.0);
        return (int) Math.round(-drawerWidth * (1.0 - t) * (1.0 - t));
    }
    private void slideWidgets() {
        int offset = slideOffset();
        widgetX.forEach((widget, x) -> widget.setX(x + offset + (tabs.contains(widget)||widget==campaignAdminButton||widget==campaignVoteButton||widget==playerAssetsButton||widget==screenMenuButton ? sidebarTransitionOffset() : 0)));
    }

    private int sidebarTransitionOffset() {
        double t = Math.min(1.0, (System.currentTimeMillis() - sidebarTransitionAt) / 180.0);
        return (int) Math.round(-23 * (1.0 - t) * (1.0 - t));
    }

    private com.wildfire.gui.CameraButton cameraButton;
    private void updateWidgets() {
        if(cameraButton!=null){cameraButton.visible=tab!=2;cameraButton.active=cameraButton.visible;}
        if(messageInput!=null){
            int lines=Math.max(1,font.split(Component.literal(messageInput.getValue()),Math.max(1,messageInput.getWidth()-16)).size());
            int inputHeight=Math.min(Math.max(20,lines*9+12),Math.max(20,Math.min(66,height/4)));
            long now=System.nanoTime();
            double elapsed=inputAnimationTime==0?0:Math.min(.05,(now-inputAnimationTime)/1_000_000_000d);
            inputAnimationTime=now;
            animatedInputHeight+=(inputHeight-animatedInputHeight)*(1-Math.exp(-18*elapsed));
            if(Math.abs(animatedInputHeight-inputHeight)<.15)animatedInputHeight=inputHeight;
            int shown=(int)Math.round(animatedInputHeight);
            int textHeight=Math.min(shown,lines*9+8);
            messageInput.setHeight(textHeight);
            messageInput.setY(height-50-shown+(shown-textHeight)/2);
        }
        for(var button:stickerPacks)button.setY(height-104-inputExpansion());
        for(var button:emojiCategories)button.setY(height-104-inputExpansion());
        if (groupConversation() && com.wildfire.client.music.GroupInbox.group(groupId()) == null) {
            conversationPartner = null; reactionPicker = false; com.wildfire.client.music.GroupInbox.close();
        }
        if (manageGroup != null) manageGroup.visible = tab == 2 && groupConversation() && closingAt == 0;
        for (var button : tabs) button.visible = tab != 2;
        if(campaignAdminButton!=null)campaignAdminButton.visible=tab!=2&&closingAt==0&&com.wildfire.client.music.CampaignClient.admin();
        if(campaignVoteButton!=null)campaignVoteButton.visible=tab!=2&&closingAt==0&&!com.wildfire.client.music.CampaignClient.active().isEmpty();
        if(playerAssetsButton!=null)playerAssetsButton.visible=false;
        if(screenMenuButton!=null){screenMenuButton.visible=tab!=2&&closingAt==0&&!com.wildfire.client.screens.ScreenInteraction.nearby().isEmpty();screenMenuButton.active=screenMenuButton.visible;}
        var navigation=new ArrayList<AbstractWidget>(tabs);
        if(campaignAdminButton!=null)navigation.add(campaignAdminButton);
        if(campaignVoteButton!=null)navigation.add(campaignVoteButton);
        if(playerAssetsButton!=null)navigation.add(playerAssetsButton);
        if(screenMenuButton!=null)navigation.add(screenMenuButton);
        int visibleNavigation=(int)navigation.stream().filter(button->button.visible).count();
        int navigationPitch=Math.min(21,Math.max(18,(height-75)/Math.max(1,visibleNavigation)));
        int navigationSlot=0;
        for(var button:navigation)if(button.visible)button.setY(40+navigationSlot++*navigationPitch);
        int count = tab == 1 ? friendRows().size() : tab == 2 ? PrivateMessageInbox.conversations().size()
            : NotificationInbox.entries().size();
        int perPage = tab == 1 ? friendPageSize() : notificationPageSize();
        page = Math.min(page, Math.max(0, (count - 1) / perPage));
        if (friendName != null) friendName.visible = tab == 1;
        if (friendSearch != null) friendSearch.visible = tab == 1;
        if (addFriend != null) addFriend.visible = tab == 1;
        if (backToChats != null) backToChats.visible = false;
        if (backToNotifications != null) backToNotifications.visible = tab == 0 && selectedNotice != null;
        if (messageInput != null) messageInput.visible = tab == 2 && conversationPartner != null;
        if (sendMessage != null) sendMessage.active = messageInput != null && !messageInput.getValue().isBlank()
            && minecraft.player != null && minecraft.getConnection() != null && pendingGroupSend.isEmpty();
        if (sendMessage != null) sendMessage.visible = tab == 2 && conversationPartner != null;
        if(attachImage!=null)attachImage.visible=tab==2&&conversationPartner!=null&&closingAt==0;
        boolean reactionsVisible = tab == 2 && conversationPartner != null && closingAt == 0;
        if (reactionToggle != null) reactionToggle.visible = reactionsVisible;
        if (emojiTab != null) { emojiTab.visible = reactionsVisible && reactionPicker; emojiTab.active = com.wildfire.gui.ChatReactions.pickerTabActive(stickerPicker, false); }
        if (stickerTab != null) { stickerTab.visible = reactionsVisible && reactionPicker; stickerTab.active = com.wildfire.gui.ChatReactions.pickerTabActive(stickerPicker, true); }
        if (emojiSearch != null) {
            emojiSearch.visible = reactionsVisible && reactionPicker && !stickerPicker;
            if (!emojiSearch.visible && getFocused() == emojiSearch) setFocused(messageInput);
        }
        for (int i = 0; i < emojiCategories.size(); i++) {
            var categoryButton = emojiCategories.get(i);
            categoryButton.visible = reactionsVisible && reactionPicker && !stickerPicker;
            categoryButton.active = i - 2 != emojiCategory;
        }
        var packs = stickerPackIds();
        if (!packs.contains(selectedStickerPack)||selectedStickerPack.equals("@shop")) selectedStickerPack = packs.size()>3 ? packs.get(2) : "@recent";
        int visiblePacks=Math.min(stickerPacks.size(),Math.max(1,contentWidth()/28));
        stickerPackScroll=Math.clamp(stickerPackScroll,0,Math.max(0,packs.size()-visiblePacks));
        for (int i = 0; i < stickerPacks.size(); i++) {
            var packButton = stickerPacks.get(i);
            packButton.visible = reactionsVisible && reactionPicker && stickerPicker && stickerPackIndex(i) < packs.size() && (i + 1) * 28 <= contentWidth();
            if (stickerPackIndex(i) < packs.size()) {
                String id = packs.get(stickerPackIndex(i));
                String label = com.wildfire.gui.WebStickers.entries().stream().filter(s -> s.id().startsWith(id + "/")).findFirst().map(com.wildfire.gui.WebStickers.Sticker::pack).orElse(id);
                packButton.setTooltip(Tooltip.create(Component.literal((id.equals("@recent")?"Недавние":id.equals("@favorites")?"Избранное":label)+" · Наборы: колесо мыши")));
            }
        }
        int reactionCount = stickerPicker ? selectedStickers().size() : selectedEmojis().size();
        int pickerTop = reactionPickerTop();
        if (emojiTab != null) emojiTab.setY(pickerTop + 4);
        if (stickerTab != null) stickerTab.setY(pickerTop + 4);
        if (emojiSearch != null) emojiSearch.setY(pickerTop + 27);
        int columns = reactionColumns();
        int cell = Math.max(20, contentWidth() / columns);
        int reactionPitch = stickerPicker ? 36 : 24;
        reactionScrollRow = Math.clamp(reactionScrollRow, 0, reactionMaxScroll(reactionCount));
        for (int slot = 0; slot < reactionButtons.size(); slot++) {
            var choice = reactionButtons.get(slot);
            widgetX.put(choice, contentLeft() + slot % columns * cell);
            choice.setX(contentLeft() + slideOffset() + slot % columns * cell);
            choice.setY(pickerTop + (stickerPicker ? 28 : 49) + slot / columns * reactionPitch);
            choice.setWidth(cell - 2);
            choice.setHeight(stickerPicker ? 34 : 22);
            int index = reactionScrollRow * reactionColumns() + slot;
            choice.visible = reactionsVisible && reactionPicker && slot < reactionSlots() && index < reactionCount;
            choice.active = choice.visible && minecraft.player != null && minecraft.getConnection() != null;
            if (index < reactionCount) {
                String label = stickerPicker ? selectedStickers().get(index).label()
                    : selectedEmojis().get(index).label();
                choice.setTooltip(Tooltip.create(Component.literal(label+" · ПКМ: избранное")));
            }
        }
        if (popupOnlyButton != null) {
            popupOnlyButton.visible = tab == 3;
            popupOnlyButton.setMessage(Component.literal("ЛС и упоминания: " +
                (CosmeticPreferences.popupOnly() ? "только поп-ап" : "поп-ап + чат")));
        }
        if (hurtSoundButton != null) {
            int pitch = Math.clamp((height - 102) / 8, 16, 28);
            Button[] settings = {popupOnlyButton, hurtSoundButton, cosmeticsButton, hudBellButton, hudTrackButton, listeningButton, ownListeningButton, shareListeningButton};
            for (int i = 0; i < settings.length; i++) if (settings[i] != null) {
                settings[i].setY(60 + i * pitch); settings[i].setHeight(Math.min(22, pitch - 3));
            }
            listeningButton.visible = ownListeningButton.visible = shareListeningButton.visible = tab == 3;
            listeningButton.setMessage(Component.literal("Музыка над игроками: " + (CosmeticPreferences.musicLabels() ? "показывать" : "скрыть")));
            ownListeningButton.setMessage(Component.literal("Своя музыка в F5: " + (CosmeticPreferences.ownMusicLabel() ? "показывать" : "скрыть")));
            shareListeningButton.setMessage(Component.literal("Делиться треком: " + (CosmeticPreferences.shareListening() ? "да" : "нет")));
            hurtSoundButton.visible = cosmeticsButton.visible = hudBellButton.visible = hudTrackButton.visible = tab == 3;
            hurtSoundButton.setMessage(Component.literal("Звук урона: " +
                (CosmeticPreferences.modHurtSound() ? "мод" : "Minecraft")));
            cosmeticsButton.setMessage(Component.literal("Косметика: " +
                (CosmeticPreferences.cosmeticsVisible() ? "показывать" : "скрывать")));
            hudBellButton.setMessage(Component.literal("Колокольчик вне чата: " +
                (CosmeticPreferences.hudBell() ? "показывать" : "скрыть")));
            hudTrackButton.setMessage(Component.literal("Строка трека: " +
                (CosmeticPreferences.hudTrack() ? "показывать" : "скрыть")));
        }
        if (hudPlayerButton != null) {
            Button[][] groups = {{notificationPopupsButton, popupOnlyButton, hudBellButton, vanillaChatButton, discordPresenceButton}, {cosmeticsButton, hurtSoundButton,overheadModeButton,overheadMessagesButton,overheadCamerasButton,screensEnabledButton},
                {hudPlayerButton, hudTrackButton, listeningButton, ownListeningButton, shareListeningButton,musicPriorityButton}};
            for (int group = 0; group < groups.length; group++)
                for (int row = 0; row < groups[group].length; row++) {
                    Button button = groups[group][row];
                    button.visible = tab == 3 && group == settingsCategory;
                    int pitch = Math.clamp((height - 150) / groups[group].length, 15, 28);
                    button.setY(98 + row * pitch); button.setHeight(Math.min(22, pitch - 2));
                }
            for (int group = 0; group < settingsCategories.size(); group++) {
                var button = settingsCategories.get(group);
                button.visible = tab == 3; button.active = group != settingsCategory;
            }
            hudPlayerButton.setMessage(Component.literal("Плеер вне меню: " + (CosmeticPreferences.hudPlayer() ? "показывать" : "скрыть")));
            musicPriorityButton.setMessage(Component.literal("Приоритет: "+(com.wildfire.client.music.AudioPreferences.clientPriority()?"своя музыка":"пластинки")));
            screensEnabledButton.setMessage(Component.literal("Игровые экраны: "+(com.wildfire.client.screens.ScreenQuality.enabled()?"включены":"выключены")));
            overheadModeButton.setMessage(Component.literal("Над головой: "+switch(CosmeticPreferences.overheadMode()){case MESSAGES->"сообщения";case CAMERA->"камера · ожидание";case OFF->"выключено";}));
        }
        for (int i = 0; i < rowActions.size(); i++) {
            var friends = friendRows();
            friendScroll = Math.clamp(friendScroll, 0, friendMaxScroll(friends.size()));
            int index = friendScroll / 38 + i;
            int rowY = 92 + i * 38 - friendScroll % 38;
            Button action = rowActions.get(i);
            action.setY(rowY);
            action.visible = tab == 1 && index < friends.size() && rowY >= 83 && rowY + 20 <= height - 73;
            Button message = messageActions.get(i);
            message.setY(rowY);
            message.visible = action.visible && !"outgoing".equals(friends.get(index).kind());
            boolean incoming = index < friends.size() && "incoming".equals(friends.get(index).kind());
            widgetX.put(action, contentLeft() + contentWidth() - (incoming ? 48 : 22));
            widgetX.put(message, contentLeft() + contentWidth() - (incoming ? 22 : 48));
            if (message.visible) message.setMessage(Component.literal(
                "incoming".equals(friends.get(index).kind()) ? "Отклон." : "Написать"));
            if (action.visible) action.setMessage(Component.literal(switch (friends.get(index).kind()) {
                case "incoming" -> "Принять";
                case "outgoing" -> "Отмена";
                default -> friends.get(index).name().equalsIgnoreCase(confirmRemoval) && System.currentTimeMillis() < confirmRemovalUntil ? "Точно?" : "Удалить";
            }));
            if (action.visible) {
                boolean busy = SocialClient.busy(friends.get(index).name());
                action.active = message.active = !busy;
                if (busy) action.setMessage(Component.literal("…"));
                action.setTooltip(Tooltip.create(Component.literal(action.getMessage().getString() + "\n" + ("friend".equals(friends.get(index).kind())
                    ? "Удаление требует повторного нажатия в течение 5 секунд" : "Действие с заявкой"))));
                message.setTooltip(Tooltip.create(Component.literal("incoming".equals(friends.get(index).kind())
                    ? "Отклонить заявку" : "Открыть личный чат")));
            }
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = slideOffset();
        RoundedPanels.fillRightEdges(graphics, x, 0, x + drawerWidth, height, UiTheme.BACKGROUND, true, true, 6);
        graphics.fill(x, 0, x + 23, height, UiTheme.CHROME);
        graphics.fill(x + 22, 0, x + 23, height, UiTheme.BORDER);
        RoundedPanels.fillRightEdges(graphics, x + 23, 0, x + drawerWidth, 35, UiTheme.CHROME, true, false, 6);
        graphics.fill(x+23,34,x+drawerWidth-2,35,UiTheme.BORDER);
        RoundedPanels.fillRightEdges(graphics, x + 23, height - 41, x + drawerWidth, height,
            UiTheme.CHROME, false, true, 6);
        graphics.fill(x + 23, height - 41, x + drawerWidth - 2, height - 40, UiTheme.BORDER);
        // Clip the accent to the drawer's outer top/bottom contour, including the footer.
        for (int row = 0; row < height; row++) {
            int edgeRow = Math.min(row, height - row - 1);
            int inset = edgeRow < 6 ? (int) Math.ceil(6 - Math.sqrt(36 - Math.pow(6 - edgeRow - 0.5, 2))) : 0;
            if (inset < 2) graphics.fill(x + drawerWidth - 2, row, x + drawerWidth - inset, row + 1, 0xFFB688DC);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (tab == 5 && closingAt == 0) townsPanel.tick();
        updateWidgets();
        if (tab == 2 && System.currentTimeMillis() - lastContactsRefresh > 15_000L) {
            lastContactsRefresh = System.currentTimeMillis();
            PrivateMessageInbox.requestContacts();
            com.wildfire.client.music.GroupInbox.refresh();
        }
        slideWidgets();
        renderContents(graphics, mouseX, mouseY, delta);
        updateStickerPreviewHover(mouseX,mouseY);
        if(messageInput!=null&&messageInput.visible){
            int inputHeight=(int)Math.round(animatedInputHeight);
            com.wildfire.gui.FlatButtons.plate(graphics,messageInput.getX(),height-50-inputHeight,
                messageInput.getWidth(),inputHeight,true,messageInput.isFocused(),false,UiTheme.ACCENT);
        }
        if (tab == 2 && conversationPartner != null && reactionPicker && closingAt == 0) {
            int pickerLeft = contentLeft() + slideOffset();
            graphics.nextStratum();
            RoundedPanels.fill(graphics, pickerLeft - 3, reactionPickerTop(), pickerLeft + contentWidth() + 3,
                height - 75 - inputExpansion(), UiTheme.CHROME, 5);
            String caption = stickerPicker && com.wildfire.gui.WebStickers.entries().isEmpty() ? com.wildfire.gui.WebStickers.status()
                : (stickerPicker && selectedStickerPack.startsWith("@") ? (selectedStickerPack.equals("@favorites")?"Избранное":"Недавние")+" · "
                    : !stickerPicker&&emojiCategory<0 ? (emojiCategory==-1?"Избранное":"Недавние")+" · "
                    : stickerPicker && !selectedStickers().isEmpty() ? selectedStickers().getFirst().caption() + " · " : "")
                    + "ПКМ: избранное · Колесо: прокрутка";
            if(stickerPicker&&!selectedStickerPack.startsWith("@")&&!selectedStickers().isEmpty()){
                var sticker=selectedStickers().getFirst();
                String author=sticker.author().isBlank()?"":" · Автор: "+(sticker.author().equals("System")?"Сервер":sticker.author());
                caption=font.plainSubstrByWidth(sticker.pack(),Math.max(1,contentWidth()-8-font.width(author)))+author;
            }
            graphics.text(font, font.plainSubstrByWidth(caption, Math.max(1, contentWidth() - 8)), pickerLeft + 4, height - 124 - inputExpansion(), UiTheme.MUTED, false);
            int count = stickerPicker ? selectedStickers().size() : selectedEmojis().size();
            int maxScroll = reactionMaxScroll(count);
            if (maxScroll > 0) {
                int top = reactionPickerTop() + (stickerPicker ? 28 : 49);
                int rows = reactionSlots() / reactionColumns();
                int track = rows * (stickerPicker ? 36 : 24) - 2;
                int thumb = Math.max(8, track * rows / (rows + maxScroll));
                int y = top + (track - thumb) * reactionScrollRow / maxScroll;
                graphics.fill(pickerLeft + contentWidth() - 2, top, pickerLeft + contentWidth(), top + track, UiTheme.BACKGROUND);
                graphics.fill(pickerLeft + contentWidth() - 2, y, pickerLeft + contentWidth(), y + thumb, UiTheme.MUTED);
            }
        }
        // Widgets must be above cards, not covered by their backgrounds.
        graphics.nextStratum();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (closingAt == 0 && tab == 2 && (returningSticker!=null ||
            heldSticker != null && (heldStickerButton == null || reactionPicker && stickerPicker)
            && System.nanoTime() - stickerPressedAt >= 1_000_000_000L)) {
            boolean returning=returningSticker!=null;
            if(!returning)stickerPreviewShown = true;
            graphics.nextStratum();
            int left=contentLeft()+slideOffset(),top=74;
            int bottom=messageInput==null?height-78:messageInput.getY()-8;
            int panelWidth=contentWidth(),panelHeight=Math.max(1,bottom-top);
            double progress=Math.clamp((System.nanoTime()-stickerPressedAt-1_000_000_000L)/220_000_000d,0,1);
            double eased=1-Math.pow(1-progress,3);
            if(returning){
                double retreat=Math.clamp((System.nanoTime()-stickerReleasedAt)/220_000_000d,0,1);
                eased=stickerReturnFrom*(1-retreat*retreat*(3-2*retreat));
                if(retreat>=1)returningSticker=null;
            }
            int targetSize=Math.max(1,Math.min(panelWidth-24,panelHeight-24));
            int startSize=(returning?returningFromPicker:heldStickerButton!=null)?Math.min(28,targetSize):Math.min(110,targetSize);
            int size=(int)Math.round(startSize+(targetSize-startSize)*eased);
            double targetX=left+panelWidth/2d,targetY=top+panelHeight/2d;
            int x=(int)Math.round(stickerPreviewOriginX+(targetX-stickerPreviewOriginX)*eased-size/2d);
            int y=(int)Math.round(stickerPreviewOriginY+(targetY-stickerPreviewOriginY)*eased-size/2d);
            graphics.enableScissor(left,top,left+panelWidth,bottom);
            graphics.fill(left,top,left+panelWidth,bottom,((int)(125*eased)<<24));
            RoundedPanels.fill(graphics,x-8,y-8,x+size+8,y+size+8,
                ((int)(240*eased)<<24)|(UiTheme.BACKGROUND&0xFFFFFF),8);
            var preview=returning?returningSticker:heldSticker;
            if(preview!=null)com.wildfire.gui.WebStickers.drawPreview(graphics, preview, x, y, size);
            graphics.disableScissor();
        }
    }

    private void renderContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (tab == 2) renderConversationSidebar(graphics, mouseX, mouseY);
        int left = contentLeft() + slideOffset();
        int available = contentWidth();
        String heading=switch(tab){case 1->"Друзья";case 2->"Чат";case 3->"Настройки";case 5->"Города";default->"Уведомления";};
        int iconSpacing=tab==0||tab==3?26:32;
        int headingWidth=iconSpacing+font.width(heading);
        int headingX=slideOffset()+23+(drawerWidth-23-headingWidth)/2;
        if(tab==5)com.wildfire.gui.TownsPanel.icon(graphics,headingX,7);
        else com.wildfire.gui.ColorMenuTitleIcons.draw(graphics,tab,headingX,7);
        graphics.text(font,heading,headingX+iconSpacing,13,UiTheme.TEXT,false);
        renderPlayer(graphics, mouseX, mouseY);
        if (minecraft.player != null) {
            PlayerFaceExtractor.extractRenderState(graphics, minecraft.player.getSkin(), left, height - 35, 18);
            String role = SocialClient.roleName();
            String identity = minecraft.player.getName().getString();
            graphics.text(font, font.plainSubstrByWidth(identity, available - 26),
                left + 24, height - 32, 0xFFE8EDF3, false);
            String expiry=SocialClient.expiryLabel();
            String privilege="Привилегия: "+(role.isBlank()?"Игрок":role);
            String term=expiry.isBlank()?"":" · Срок: "+expiry;
            graphics.text(font,font.plainSubstrByWidth(privilege+term,Math.max(1,available-26)),
                left+24,height-21,UiTheme.MUTED,false);
            graphics.item(new ItemStack(Items.DIAMOND),left,height-16);
            graphics.text(font,font.plainSubstrByWidth("Баланс: "+
                com.wildfire.client.music.CardBalanceClient.display(),Math.max(1,available-26)),
                left+24,height-10,0xFFFFD28F,false);
        }
        int perPage = tab == 1 ? friendPageSize() : notificationPageSize();
        int count = tab == 0 ? NotificationInbox.entries().size() : tab == 1 ? friendRows().size()
            : tab == 2 ? PrivateMessageInbox.conversations().size() : 0;
        if (tab == 3) return;
        if (tab == 5) { townsPanel.render(graphics,left,46,available,height-48,mouseX,mouseY); return; }
        if (tab == 2 && conversationPartner != null) {
            renderConversation(graphics, left, available,mouseX,mouseY);
            return;
        }
        if (tab == 2) {
            graphics.text(font, "Выберите чат слева", left, 48, 0xFFB3BDC8, false);
            graphics.text(font, "Кнопка + создаёт группу", left, 66, UiTheme.MUTED, false);
            if (!com.wildfire.client.music.GroupInbox.supported()) graphics.text(font,
                font.plainSubstrByWidth(com.wildfire.client.music.GroupInbox.status(),available),left,84,UiTheme.MUTED,false);
            return;
        }
        if (tab == 0 && selectedNotice != null) {
            renderNotice(graphics, left, available);
            return;
        }
        if (tab == 0) {
            renderNotificationList(graphics,left,available);
            return;
        }
        if (count == 0) {
            graphics.text(font, tab == 1 ? (SocialClient.loaded() ? (friendSearch != null && !friendSearch.getValue().isBlank()
                ? "По вашему поиску никого нет" : "Добавьте друга по нику ниже") : SocialClient.status())
                : tab == 2 ? "Диалогов пока нет" : "Пока нет уведомлений", left, 91, 0xFFB3BDC8, false);
            renderFriendSuggestions(graphics, mouseX, mouseY);
            return;
        }
        if (tab == 1) {
            long friends = SocialClient.rows().stream().filter(row -> "friend".equals(row.kind())).count();
            long lastTwo = friends % 100, lastOne = friends % 10;
            String label = lastTwo >= 11 && lastTwo <= 14 ? "друзей"
                : lastOne == 1 ? "друг" : lastOne >= 2 && lastOne <= 4 ? "друга" : "друзей";
            long online = SocialClient.rows().stream().filter(row -> "friend".equals(row.kind()) && "in_game".equals(row.status())).count();
            long requests = SocialClient.rows().stream().filter(row -> "incoming".equals(row.kind())).count();
            graphics.text(font, font.plainSubstrByWidth(friends + " " + label + " · онлайн " + online + " · заявки " + requests, available),
                left, 48, 0xFFB9C2CC, false);
        }
        if (tab == 1) graphics.enableScissor(left - 4, 83, left + available + 4, Math.max(84, height - 73));
        int firstRow = tab == 1 ? friendScroll / 38 : page * perPage;
        for (int row = 0; row < perPage && firstRow + row < count; row++) {
            int y = 87 + row * 38 - (tab == 1 ? friendScroll % 38 : 0);
            RoundedPanels.fill(graphics, left - 4, y - 4, left + available + 4, y + 32, UiTheme.SURFACE);
            graphics.fill(left - 4, y - 4, left - 1, y + 32, ICON_COLORS[tab]);
            if (tab == 1) {
                var entry = friendRows().get(firstRow + row);
                var skin = partnerSkin(entry.name());
                if (skin != null) com.wildfire.gui.ChatAvatars.drawTexture(graphics, skin, left + 3, y + 2, 20);
                else graphics.item(new ItemStack(Items.PLAYER_HEAD), left + 4, y + 2);
                int statusColor = "in_game".equals(entry.status()) ? 0xFF36CF79
                    : "friend".equals(entry.kind()) ? 0xFF929AA6 : 0xFFFFC46A;
                statusDot(graphics, left + 20, y + 20, statusColor);
                com.wildfire.gui.ModPresence.draw(graphics,entry.name(),left+3,y+15,mouseX,mouseY);
                graphics.text(font, font.plainSubstrByWidth(identity(entry.role(), entry.name()), Math.max(12, available - 85)),
                    left + 29, y + 3, 0xFFE8EDF3, false);
                String detail = switch (entry.kind()) {
                    case "incoming" -> "Входящая заявка";
                    case "outgoing" -> "Исходящая заявка";
                    default -> "in_game".equals(entry.status()) ? "В сети" : PrivateMessageInbox.lastSeenLabel(entry.name());
                };
                graphics.text(font, font.plainSubstrByWidth(detail, Math.max(12, available - 85)),
                    left + 29, y + 16, 0xFFB9C2CC, false);
            } else if (tab == 2) {
                var conversation = PrivateMessageInbox.conversations().get(page * perPage + row);
                var skin = partnerSkin(conversation.partner());
                if (skin != null) com.wildfire.gui.ChatAvatars.drawTexture(graphics, skin, left + 3, y + 2, 20);
                else graphics.item(new ItemStack(Items.PLAYER_HEAD), left + 4, y + 2);
                if (conversation.online()) statusDot(graphics, left + 20, y + 20,
                    PrivateMessageInbox.statusColor(conversation.partner()));
                graphics.text(font, font.plainSubstrByWidth(conversation.partner(), available - 68),
                    left + 29, y + 1, 0xFFE8EDF3, false);
                com.wildfire.gui.ModPresence.draw(graphics,conversation.partner(),left+3,y+15,mouseX,mouseY);
                String detail = (conversation.role().isBlank() ? "" : conversation.role() + " · ")
                    + PrivateMessageInbox.presence(conversation.partner())
                    + (conversation.unread() > 0 ? " · ЛС: " + conversation.unread() : "");
                graphics.text(font, font.plainSubstrByWidth(detail, available - 68),
                    left + 29, y + 17, 0xFFB9C2CC, false);
            } else {
                var entry = NotificationInbox.entries().get(page * perPage + row);
                graphics.text(font, font.plainSubstrByWidth("[" + entry.category() + "] " + entry.title(), available - 58),
                    left + 4, y, 0xFFE8EDF3, false);
                graphics.text(font, TIME.format(Instant.ofEpochMilli(entry.receivedAt())),
                    left + available - 30, y, 0xFF9DA7B2, false);
                graphics.text(font, font.plainSubstrByWidth(entry.message(), available - 8),
                    left + 4, y + 16, 0xFFB9C2CC, false);
            }
        }
        if (tab == 1) {
            graphics.disableScissor();
            int viewport = Math.max(1, height - 156), max = friendMaxScroll(count);
            if (max > 0) {
                int thumb = Math.max(12, viewport * viewport / (count * 38));
                int top = 83 + (viewport - thumb) * friendScroll / max;
                RoundedPanels.fillBar(graphics, left + available + 5, top, left + available + 7, top + thumb, UiTheme.MUTED);
            }
        }
        renderFriendSuggestions(graphics, mouseX, mouseY);
    }


    private void renderNotificationList(GuiGraphicsExtractor graphics,int left,int available) {
        var entries=NotificationInbox.entries();var layout=notificationLayout(entries);
        if(entries.isEmpty()){graphics.text(font,"Пока нет уведомлений",left+4,layout.top()+8,0xFFB3BDC8,false);return;}
        graphics.enableScissor(left-4,layout.top(),left+available+4,layout.bottom());
        try{
            for(int index=layout.first();index<layout.end();index++){
                int y=layout.rowTop(index);var entry=entries.get(index);
                RoundedPanels.fill(graphics,left-4,y,left+available+4,y+NoticeListLayout.CARD_HEIGHT,UiTheme.SURFACE);
                graphics.fill(left-4,y,left-1,y+NoticeListLayout.CARD_HEIGHT,ICON_COLORS[0]);
                String sender = NotificationInbox.sender(entry.category(), entry.title(), entry.message());
                int inset = sender.isEmpty() ? 4 : 30;
                if (!sender.isEmpty()) renderNotificationAvatar(graphics, sender, left + 3, y + 7, 20);
                String heading = sender.isEmpty() ? "["+entry.category()+"] "+entry.title() : NotificationInbox.playerIdentity(sender);
                graphics.text(font,font.plainSubstrByWidth(heading,Math.max(1,available-54-inset)),left+inset,y+4,0xFFE8EDF3,false);
                graphics.text(font,TIME.format(Instant.ofEpochMilli(entry.receivedAt())),left+available-30,y+4,0xFF9DA7B2,false);
                graphics.text(font,font.plainSubstrByWidth(entry.message(),Math.max(1,available-inset-4)),left+inset,y+20,0xFFB9C2CC,false);
            }
        }finally{graphics.disableScissor();}
        if(layout.maxOffset()>0){
            int viewport=layout.bottom()-layout.top(),content=entries.size()*NoticeListLayout.PITCH-2;
            int thumb=Math.min(viewport,Math.max(12,viewport*viewport/content));
            int y=layout.top()+(viewport-thumb)*layout.offset()/layout.maxOffset();
            RoundedPanels.fill(graphics,left+available+6,y,left+available+8,y+thumb,UiTheme.ACCENT);
        }
    }
    private void renderConversationSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = slideOffset() + sidebarTransitionOffset() + 2;
        RoundedPanels.fill(graphics, x, 40, x + 18, 58, 0xFF343640);
        graphics.text(font, "←", x + 5, 44, 0xFFFFFFFF, false);
        boolean createHovered = mouseX >= x && mouseX < x + 18 && mouseY >= 64 && mouseY < 82;
        RoundedPanels.fill(graphics,x,64,x+18,82,createHovered ? 0xFF42414F : 0xFF343640);
        groupFaces(graphics,"",x+2,66,14);
        // Creation action, distinct from the four-avatar icons of existing groups.
        graphics.fill(x+6,68,x+14,78,0xFF141820);
        graphics.fill(x+5,69,x+15,77,0xFF141820);
        graphics.fill(x+9,70,x+11,76,0xFFF1F3FA);
        graphics.fill(x+7,72,x+13,74,0xFFF1F3FA);
        if(createHovered) graphics.setTooltipForNextFrame(Component.literal(
            com.wildfire.client.music.GroupInbox.supported() ? "Создать групповой чат" : "Группы недоступны на этом сервере"),mouseX,mouseY);
        var conversations = dialogs();
        conversationSidebarScroll = Math.min(conversationSidebarScroll,
            Math.max(0, conversations.size() - sidebarSlots()));
        for (int row = 0; row < sidebarSlots() && conversationSidebarScroll + row < conversations.size(); row++) {
            var conversation = conversations.get(conversationSidebarScroll + row);
            int y = 85 + row * 21;
            boolean selected = conversation.partner().equalsIgnoreCase(conversationPartner);
            RoundedPanels.fill(graphics, x, y, x + 18, y + 18, selected ? 0xFF42414F : 0xFF242831);
            var skin = conversation.group() ? null : partnerSkin(conversation.partner());
            if (conversation.group()) groupFaces(graphics,com.wildfire.client.music.GroupInbox.id(conversation.partner()),x+2,y+2,14);
            else if (skin != null) com.wildfire.gui.ChatAvatars.drawTexture(graphics, skin, x + 2, y + 2, 14);
            else graphics.item(new ItemStack(Items.PLAYER_HEAD), x + 1, y + 1);
            if (conversation.online()) statusDot(graphics, x + 15, y + 15,
                PrivateMessageInbox.statusColor(conversation.partner()));
            if (conversation.unread() > 0) graphics.fill(x + 12, y, x + 18, y + 6, 0xFFE13251);
            if (mouseX >= x && mouseX < x+18 && mouseY >= y && mouseY < y+18) graphics.setTooltipForNextFrame(
                Component.literal((conversation.group()?"Группа: ":"")+conversation.title()+(conversation.unread()>0?" · "+conversation.unread():"")),mouseX,mouseY);
            if(!conversation.group())com.wildfire.gui.ModPresence.draw(graphics,conversation.partner(),x,y+9,mouseX,mouseY);
        }
    }

    private static void groupFaces(GuiGraphicsExtractor g,String id,int x,int y,int size) {
        var names=com.wildfire.client.music.GroupInbox.faces(id);
        var group=com.wildfire.client.music.GroupInbox.group(id);
        int count=id.isEmpty()?4:group==null?Math.min(4,names.size()):Math.min(4,group.count());
        if(count==0)return;
        int face=count==1?size:(size-2)/2,pitch=size-face;
        for(int i=0;i<count;i++){
            int fx=x+(count==3&&i==0?(size-face)/2:count==3?(i-1)*pitch:(i%2)*pitch);
            int fy=y+(count==2?(size-face)/2:count==3?(i==0?0:pitch):(i/2)*pitch);
            g.fill(fx-1,fy-1,fx+face+1,fy+face+1,0xFF141820);
            var skin=i<names.size()?partnerSkin(names.get(i)):null;
            if(skin!=null)com.wildfire.gui.ChatAvatars.drawTexture(g,skin,fx,fy,face);
            else {
                g.fill(fx,fy,fx+face,fy+face,0xFFB5A4A0);
                g.fill(fx,fy,fx+face,fy+Math.max(1,face/3),0xFF505064);
                g.fill(fx+1,fy+face/2,fx+2,fy+face/2+1,0xFF272C38);
                g.fill(fx+face-2,fy+face/2,fx+face-1,fy+face/2+1,0xFF272C38);
            }
        }
    }

    public static void renderNotificationAvatar(GuiGraphicsExtractor graphics, String name, int x, int y, int size) {
        var skin = partnerSkin(name);
        if (skin != null) com.wildfire.gui.ChatAvatars.drawTexture(graphics, skin, x, y, size);
        else graphics.item(new ItemStack(Items.PLAYER_HEAD), x, y);
    }

    private static net.minecraft.resources.Identifier partnerSkin(String name) {
        return com.wildfire.gui.ChatAvatars.texture(name);
    }

    private static void statusDot(GuiGraphicsExtractor graphics, int x, int y) {
        statusDot(graphics, x, y, 0xFF36CF79);
    }

    private static void statusDot(GuiGraphicsExtractor graphics, int x, int y, int color) {
        int[] halfWidths = {1, 2, 2, 2, 1};
        for (int row = 0; row < halfWidths.length; row++)
            graphics.fill(x - halfWidths[row], y + row - 2, x + halfWidths[row] + 1, y + row - 1, color);
    }

    private List<FormattedCharSequence> noticeLines(int width) {
        if (selectedNotice == null) return List.of();
        return font.split(Component.literal(selectedNotice.message()), width);
    }

    private void renderNotice(GuiGraphicsExtractor graphics, int left, int available) {
        String sender = NotificationInbox.sender(selectedNotice.category(), selectedNotice.title(), selectedNotice.message());
        int profileLeft=left+backToNotifications.getWidth()+8;
        if (!sender.isEmpty()) renderNotificationAvatar(graphics, sender, profileLeft, 43, 20);
        int titleLeft=profileLeft+(sender.isEmpty()?0:26);
        graphics.text(font, font.plainSubstrByWidth(sender.isEmpty() ? selectedNotice.title() : NotificationInbox.playerIdentity(sender), Math.max(1, left+available-titleLeft)),
            titleLeft, 48, 0xFFE8EDF3, false);
        var lines = noticeLines(available - 12);
        int visibleLines = Math.max(1, (height - 160) / 11);
        noticeScroll = Math.min(noticeScroll, Math.max(0, lines.size() - visibleLines));
        int end = Math.min(lines.size(), noticeScroll + visibleLines);
        for (int i = noticeScroll; i < end; i++)
            graphics.text(font, lines.get(i), left + 4, 76 + (i - noticeScroll) * 11, 0xFFE8EDF3, false);
        if (end < lines.size()) graphics.text(font, "▼ Прокрутите ниже", left + 4, height - 73, 0xFF9DA7B2, false);
    }

    private final class ReactionTabButton extends Button {
        private final boolean stickers;
        ReactionTabButton(boolean stickers, int x, int y, int width, OnPress action) {
            super(x,y,width,20,Component.literal(stickers ? "Стикеры" : "Эмодзи"),action,DEFAULT_NARRATION); this.stickers=stickers;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean selected=stickerPicker==stickers;
            com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,selected||isHoveredOrFocused(),false,UiTheme.ACCENT);
            graphics.text(font,getMessage(),getX()+(getWidth()-font.width(getMessage()))/2,getY()+5,selected?UiTheme.TEXT:UiTheme.MUTED,false);
            if(selected)graphics.fill(getX()+8,getY()+18,getX()+getWidth()-8,getY()+20,UiTheme.ACCENT);
        }
        @Override public boolean isMouseOver(double x, double y) { return visible && super.isMouseOver(x,y); }
    }
    private final class ReactionChoiceButton extends Button {
        private final int slot;
        ReactionChoiceButton(int slot, int x, int y, int width, OnPress action) {
            super(x, y, width, 22, Component.literal("Эмодзи / стикер"), action, DEFAULT_NARRATION);
            this.slot = slot;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            if(stickerPicker||isHoveredOrFocused())
                com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,UiTheme.ACCENT);
            int index = reactionScrollRow * reactionColumns() + slot;
            int size = Math.max(1, Math.min(stickerPicker ? 28 : 18, getWidth() - (stickerPicker ? 6 : 4)));
            if (stickerPicker && index < selectedStickers().size())
                com.wildfire.gui.WebStickers.draw(graphics, selectedStickers().get(index), getX() + (getWidth() - size) / 2, getY() + 3, size);
            else if (!stickerPicker && index < selectedEmojis().size())
                com.wildfire.gui.ChatReactions.draw(graphics, selectedEmojis().get(index), getX() + (getWidth() - size) / 2, getY() + 2, size);
            String id=stickerPicker&&index<selectedStickers().size()?selectedStickers().get(index).id()
                :!stickerPicker&&index<selectedEmojis().size()?selectedEmojis().get(index).id():null;
            if(id!=null&&com.wildfire.gui.ReactionHistory.get().favorite(stickerPicker?"sticker":"emoji",id))
                graphics.text(font,Component.literal("★"),getX()+getWidth()-8,getY()+1,0xFFFFCE64,false);
        }
        @Override public boolean isMouseOver(double x, double y) { return visible && super.isMouseOver(x,y); }
    }
    private int reactionColumns() { return reactionColumns(stickerPicker); }
    private int reactionColumns(boolean stickers) { return Math.max(1, Math.min(stickers ? 6 : 14, contentWidth() / (stickers ? 36 : 24))); }
    private List<com.wildfire.gui.ChatReactions.Reaction> selectedEmojis() {
        String query = emojiSearch == null ? "" : emojiSearch.getValue();
        String key = emojiCategory + ":" + query+":"+com.wildfire.gui.ReactionHistory.get().revision();
        if (!key.equals(emojiFilterKey)) {
            var matches=com.wildfire.gui.EmojiCatalog.select(Math.max(0,emojiCategory), query);
            if(emojiCategory<0){
                var byId=new java.util.HashMap<String,com.wildfire.gui.ChatReactions.Reaction>();
                matches.forEach(e->byId.put(e.id(),e));
                filteredEmojis=com.wildfire.gui.ReactionHistory.get().ids("emoji",emojiCategory==-1).stream()
                    .map(byId::get).filter(java.util.Objects::nonNull).toList();
            }else filteredEmojis=matches;
            emojiFilterKey = key;
        }
        return filteredEmojis;
    }
    private List<String> stickerPackIds() {
        var entries=com.wildfire.gui.WebStickers.entries();
        long revision=com.wildfire.gui.ReactionHistory.get().revision();
        if(packIdsCatalog==entries&&packIdsRevision==revision)return cachedPackIds;
        packIdsRevision=revision;
        var ids=new ArrayList<String>();ids.add("@recent");ids.add("@favorites");
        ids.addAll(entries.stream().map(s -> s.id().split("/")[0]).distinct().filter(id->com.wildfire.gui.ReactionHistory.get().installed(id)).toList());
        ids.add("@shop");
        packIdsCatalog=entries;cachedPackIds=List.copyOf(ids);return cachedPackIds;
    }
    /** Keep recents and favorites pinned while scrolling regular packs. */
    private final class HistoryCategoryButton extends Button {
        private final boolean favorite;
        HistoryCategoryButton(boolean favorite,int x,int y,int width,OnPress action){
            super(x,y,width,20,Component.literal(favorite?"Избранное":"Недавние"),action,DEFAULT_NARRATION);this.favorite=favorite;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
            com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,UiTheme.ACCENT);
            com.wildfire.gui.ReactionIcons.draw(graphics,favorite,getX(),getY(),getWidth(),getHeight(),UiTheme.TEXT);
        }
    }
    private int stickerPackIndex(int slot){return slot<2?slot:slot+stickerPackScroll;}
    private final class StickerPackButton extends Button {
        private final int slot;
        StickerPackButton(int slot, int x, int y, OnPress action) { super(x,y,26,24,Component.literal("Набор"),action,DEFAULT_NARRATION); this.slot=slot; }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            var packs=stickerPackIds(); if(stickerPackIndex(slot)>=packs.size())return;
            String id=packs.get(stickerPackIndex(slot));
            com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,id.equals(selectedStickerPack)||isHoveredOrFocused(),false,UiTheme.ACCENT);
            if(id.startsWith("@")){
                if(id.equals("@shop")){graphics.text(font,"+",getX()+9,getY()+8,UiTheme.TEXT,false);return;}
                com.wildfire.gui.ReactionIcons.draw(graphics,id.equals("@favorites"),getX(),getY(),getWidth(),getHeight(),UiTheme.TEXT);return;
            }
            com.wildfire.gui.WebStickers.entries().stream().filter(s -> s.id().startsWith(id + "/")).findFirst()
                .ifPresent(s -> com.wildfire.gui.WebStickers.draw(graphics,s,getX()+3,getY()+2,20));
        }
        @Override public boolean isMouseOver(double x, double y) { return visible && super.isMouseOver(x,y); }
    }
    private int reactionMaxScroll(int count) { return Math.max(0, (count + reactionColumns() - 1) / reactionColumns() - reactionSlots() / reactionColumns()); }
    private int reactionSlots() { return reactionSlots(stickerPicker); }
    private int reactionSlots(boolean stickers) {
        return stickers ? reactionColumns(true) * com.wildfire.gui.ReactionPickerLayout.stickerRows(height)
            : reactionColumns(false) * com.wildfire.gui.ReactionPickerLayout.emojiRows(height);
    }
    private int reactionPickerTop() {
        // One shared panel boundary; only the cell sizes differ between tabs.
        return com.wildfire.gui.ReactionPickerLayout.top(height)-inputExpansion();
    }
    private int inputExpansion(){return messageInput==null?0:Math.max(0,(int)Math.round(animatedInputHeight)-20);}
    private record MessageBubble(boolean outgoing, List<FormattedCharSequence> lines, String time, int width, int height,
        com.wildfire.gui.ChatReactions.Reaction sticker, com.wildfire.gui.WebStickers.Sticker webSticker, String sender,String image,boolean read,String receiptKey) {}
    private final com.wildfire.gui.MessageReceiptReveal receiptReveal=new com.wildfire.gui.MessageReceiptReveal();
    private record ChatMessage(long receivedAt,boolean outgoing,String body,String sender,boolean read) {}
    private List<ChatMessage> chatMessages() {
        if (groupConversation()) return com.wildfire.client.music.GroupInbox.messages(groupId()).stream()
            .map(m -> new ChatMessage(m.time(),com.wildfire.client.music.GroupInbox.outgoing(m),m.body(),m.name(),com.wildfire.client.music.GroupInbox.readByAll(groupId(),m.id()))).toList();
        return PrivateMessageInbox.messages(conversationPartner).stream().map(m -> new ChatMessage(m.receivedAt(),m.outgoing(),m.body(),"",PrivateMessageInbox.isRead(conversationPartner,m.receivedAt()))).toList();
    }

    private List<MessageBubble> conversationBubbles(int available) {
        var bubbles = new ArrayList<MessageBubble>();
        if (conversationPartner == null) return bubbles;
        int maxWidth = Math.max(24, (available - 8) * 3 / 4);
        int textWidth = Math.max(8, maxWidth - 16);
        for (var message : chatMessages()) {
            var sticker = com.wildfire.gui.ChatReactions.sticker(message.body());
            var webSticker = com.wildfire.gui.WebStickers.message(message.body());
            String image=com.wildfire.gui.ChatImages.imageId(message.body());
            String displayBody=webSticker==null&&com.wildfire.gui.WebStickers.isMessage(message.body())?com.wildfire.gui.WebStickers.missingLabel():message.body();
            var lines = sticker == null && webSticker == null && image==null ? font.split(com.wildfire.gui.ChatLinks.text(displayBody), textWidth)
                : List.<FormattedCharSequence>of();
            String time = TIME.format(Instant.ofEpochMilli(message.receivedAt()));
            int usedWidth = Math.max(font.width(time)+(message.outgoing()?14:0),font.width(message.sender()));
            for (var line : lines) usedWidth = Math.max(usedWidth, font.width(line));
            int bubbleWidth = Math.min(maxWidth, image!=null?160:webSticker != null ? 120 : sticker == null ? Math.max(40, usedWidth + 16) : 80);
            bubbles.add(new MessageBubble(message.outgoing(), lines, time, bubbleWidth,
                (sticker == null && webSticker == null && image==null ? Math.max(1, lines.size()) * 11 + 22 : bubbleWidth + 12)
                    + (message.sender().isEmpty()?0:11), sticker, webSticker, message.sender(),image,message.read(),
                    conversationPartner+":"+message.receivedAt()+":"+message.sender()+":"+message.body()));
        }
        return bubbles;
    }

    private void openContactProfile(){
        attachmentDraft=messageInput==null?"":messageInput.getValue();
        String partner=conversationPartner;
        SocialClient.request();
        minecraft.gui.setScreen(new com.wildfire.gui.ContactProfileScreen(this,partner,
            ()->PrivateMessageInbox.presence(partner),
            ()->{var row=SocialClient.rows().stream().filter(r->r.name().equalsIgnoreCase(partner)).findFirst().orElse(null);
                return row==null?"Добавить в друзья":switch(row.kind()){case "friend"->"Уже в друзьях";case "incoming"->"Принять заявку";case "outgoing"->"Отменить заявку";default->"Добавить в друзья";};},
            ()->!SocialClient.busy(partner)&&SocialClient.rows().stream().noneMatch(r->r.name().equalsIgnoreCase(partner)&&"friend".equals(r.kind())),
            ()->{boolean outgoing=SocialClient.rows().stream().anyMatch(r->r.name().equalsIgnoreCase(partner)&&"outgoing".equals(r.kind()));SocialClient.action(outgoing?"cancel":"add",partner);},
            dev.aeltorx.inflow.client.InFlowApi.allowed()?parent->dev.aeltorx.inflow.client.InFlowApi.openPlayer(parent,partner):null,
            (g,x,y)->{var skin=partnerSkin(partner);if(skin!=null)com.wildfire.gui.ChatAvatars.drawTexture(g,skin,x,y,26);else g.item(new ItemStack(Items.PLAYER_HEAD),x+5,y+5);}));
    }

    private int conversationContentHeight(int available) {
        return conversationBubbles(available).stream().mapToInt(bubble -> bubble.height() + 6).sum();
    }

    private int conversationViewportHeight() {
        return Math.max(1, height-70-inputExpansion()-8-74);
    }

    private void renderConversation(GuiGraphicsExtractor graphics, int left, int available,int mouseX,int mouseY) {
        int panelLeft=slideOffset()+23,panelRight=slideOffset()+drawerWidth-2;
        graphics.fill(panelLeft,35,panelRight,71,UiTheme.CHROME);
        graphics.fill(panelLeft,71,panelRight,72,UiTheme.BORDER);
        graphics.text(font,"›",panelRight-10,49,UiTheme.ACCENT,false);
        if(!groupConversation()&&mouseX>=panelLeft&&mouseX<panelRight&&mouseY>=35&&mouseY<71)
            graphics.setTooltipForNextFrame(Component.literal("Открыть профиль игрока"),mouseX,mouseY);
        var skin = groupConversation() ? null : partnerSkin(conversationPartner);
        String name = font.plainSubstrByWidth(conversationIdentity(), Math.max(1, available - 100));
        var group = com.wildfire.client.music.GroupInbox.group(groupId());
        String groupActivity=com.wildfire.gui.ChatActivity.label(conversationPartner,true);
        String presence = font.plainSubstrByWidth(groupConversation() ? (!groupActivity.isEmpty()?groupActivity:group==null?"":group.count()+" участников") : PrivateMessageInbox.presence(conversationPartner), Math.max(1, available - 40));
        int groupWidth = 28 + Math.max(font.width(name), font.width(presence));
        int groupX = panelLeft + Math.max(0, (panelRight-panelLeft-groupWidth) / 2);
        if (groupConversation()) groupFaces(graphics,groupId(),groupX,43,22);
        else if (skin != null) com.wildfire.gui.ChatAvatars.drawTexture(graphics, skin, groupX, 43, 22);
        else graphics.item(new ItemStack(Items.PLAYER_HEAD), groupX + 3, 46);
        graphics.text(font, name, groupX + 28, 43, UiTheme.TEXT, false);
        if(!groupConversation())com.wildfire.gui.ModPresence.draw(graphics,conversationPartner,groupX,57,mouseX,mouseY);
        boolean online = !groupConversation() && PrivateMessageInbox.isOnline(conversationPartner);
        if (!groupConversation()) statusDot(graphics, groupX + 20, 63,
            PrivateMessageInbox.statusColor(conversationPartner));
        graphics.text(font, presence, groupX + 28, 56,
            online ? PrivateMessageInbox.statusColor(conversationPartner) : UiTheme.MUTED, false);
        var bubbles = conversationBubbles(available);
        if (bubbles.isEmpty()) {
            var lines = font.split(Component.literal(groupConversation() ? com.wildfire.client.music.GroupInbox.historyStatus(groupId()) : PrivateMessageInbox.historyStatus(conversationPartner)), Math.max(1, available - 16));
            int lineY = 84;
            for (var line : lines) { graphics.text(font, line, left + 8, lineY, UiTheme.MUTED, false); lineY += 12; }
        }
        int viewportHeight = conversationViewportHeight();
        int totalHeight = bubbles.stream().mapToInt(bubble -> bubble.height() + 6).sum();
        int maxScroll = Math.max(0, totalHeight - viewportHeight);
        conversationScroll = Math.clamp(conversationScroll, 0, maxScroll);
        int top = 74, bottom = top + viewportHeight;
        int y = top - Math.max(0, totalHeight - viewportHeight) + conversationScroll;
        graphics.enableScissor(left, top, left + available, bottom);
        try {
            for (var bubble : bubbles) {
                int x = bubble.outgoing() ? left + available - bubble.width() - 4 : left + 4;
                if (y + bubble.height() > top && y < bottom) {
                    RoundedPanels.fill(graphics, x, y, x + bubble.width(), y + bubble.height(),
                        bubble.outgoing() ? UiTheme.OUTGOING : UiTheme.INCOMING, 5);
                    int lineY = y + 7;
                    int senderInset = bubble.sender().isEmpty()?0:11;
                    if (senderInset>0) { graphics.text(font,font.plainSubstrByWidth(bubble.sender(),Math.max(1,bubble.width()-16)),x+8,lineY,UiTheme.ACCENT,false); lineY+=senderInset; }
                    if (bubble.sticker() != null) com.wildfire.gui.ChatReactions.draw(graphics,
                        bubble.sticker(), x + 8, y + 6 + senderInset, Math.max(1, bubble.width() - 16));
                    if (bubble.webSticker() != null) com.wildfire.gui.WebStickers.draw(graphics,
                        bubble.webSticker(), x + 8, y + 6 + senderInset, Math.max(1, bubble.width() - 16));
                    if(bubble.image()!=null)com.wildfire.gui.ChatImages.draw(graphics,bubble.image(),x+8,y+6+senderInset,Math.max(1,bubble.width()-16));
                    for (var line : bubble.lines()) {
                        graphics.text(font, line, x + 8, lineY, 0xFFE8EDF3, false);
                        lineY += 11;
                    }
                    graphics.text(font, bubble.time(), x + bubble.width() - font.width(bubble.time()) - 7-(bubble.outgoing()?14:0),
                        y + bubble.height() - 12, 0xFFB3BDC8, false);
                    if(bubble.outgoing())drawReceipt(graphics,x+bubble.width()-18,y+bubble.height()-10,
                        receiptReveal.update(bubble.receiptKey(),bubble.read(),System.nanoTime()));
                }
                y += bubble.height() + 6;
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private String conversationIdentity() {
        if (groupConversation()) { var group=com.wildfire.client.music.GroupInbox.group(groupId()); return group==null?"Группа":group.title(); }
        String role = PrivateMessageInbox.conversations().stream()
            .filter(row -> row.partner().equalsIgnoreCase(conversationPartner)).map(row -> row.role()).findFirst().orElse("");
        return identity(role, conversationPartner);
    }
    private static void drawReceipt(GuiGraphicsExtractor g,int x,int y,double progress){
        int from=UiTheme.MUTED,to=UiTheme.ACCENT,color=0xFF000000;
        for(int shift=0;shift<=16;shift+=8){
            int a=(from>>>shift)&255,b=(to>>>shift)&255;
            color|=((int)Math.round(a+(b-a)*progress))<<shift;
        }
        int offset=(int)Math.round(3*(1-progress));
        // One compact ligature: the second mark adds only its rising stroke,
        // rather than drawing two separate complete check symbols.
        g.fill(x+offset,y+2,x+offset+1,y+3,color);
        g.fill(x+offset+1,y+3,x+offset+2,y+4,color);
        for(int step=0;step<5;step++)g.fill(x+offset+2+step,y+4-step,x+offset+3+step,y+5-step,color);
        int length=(int)Math.round(5*progress);
        int secondColor=((int)Math.round(255*progress)<<24)|(color&0xFFFFFF);
        for(int step=0;step<length;step++)g.fill(x+6+step,y+4-step,x+7+step,y+5-step,secondColor);
    }

    private static String identity(String role, String name) {
        return role == null || role.isBlank() ? name : role + " " + name;
    }

    /** Compact now-playing card in chat; the drawer is not required. */
    public static void renderChatPlayer(GuiGraphicsExtractor graphics, int screenWidth) {
        if (!MusicClient.active()) return;
        int panelWidth = Math.min(244, screenWidth - 16);
        if (panelWidth < 140) return;
        int x = screenWidth - panelWidth - 8;
        var font = Minecraft.getInstance().font;
        RoundedPanels.fillLeft(graphics, x, 8, x + panelWidth, 65, 0xEC171B25, 6);
        RoundedPanels.fillRightBar(graphics, x + panelWidth - 3, 8, x + panelWidth, 65, MusicClient.accent());
        com.wildfire.client.music.MusicPlatformIcon.draw(graphics, MusicClient.accent(), x + 10, 16);
        graphics.text(font, "СЕЙЧАС ИГРАЕТ · " + MusicClient.position(), x + 34, 14, 0xFFB9A5D8, false);
        ModUpdate.drawScrollingTitle(graphics, MusicClient.title(), x + 34, 27, panelWidth - 44);
        graphics.text(font, MusicClient.timeLabel(), x + 10, 45, 0xFFB9C2CC, false);
        float progress = MusicClient.progress();
        graphics.fill(x + 10, 58, x + panelWidth - 10, 61, 0xFF343A43);
        if (progress >= 0) graphics.fill(x + 10, 58,
            x + 10 + (int) ((panelWidth - 20) * progress), 61, MusicClient.accent());
    }

    private int pressedMusicControl = -1;
    private boolean draggingMusicSeek;
    private float musicSeekPreview;
    private String musicSeekTrack = "";

    private static void drawSeekKnob(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int row = -3; row <= 3; row++) {
            int half = (int)Math.floor(Math.sqrt(12 - row * row));
            graphics.fill(x - half, y + row, x + half + 1, y + row + 1, color);
        }
    }

    private boolean onMusicSeek(double mouseX, double mouseY) {
        int x = Math.max(drawerWidth + 8, width - 258), panelWidth = width - x - 8;
        x += playerSlideOffset(panelWidth);
        return closingAt == 0 && panelWidth >= 140 && MusicClient.canSeek()
            && mouseX >= x + 8 && mouseX <= x + panelWidth - 8 && mouseY >= 68 && mouseY <= 83;
    }

    private void previewMusicSeek(double mouseX) {
        int x = Math.max(drawerWidth + 8, width - 258), panelWidth = width - x - 8;
        x += playerSlideOffset(panelWidth);
        musicSeekPreview = (float)Math.clamp((mouseX - x - 12) / Math.max(1, panelWidth - 24), 0d, 1d);
    }
    private long musicControlPressedUntil;
    private boolean compactPlayer(){return !MusicClient.active()&&!MusicClient.personalActive();}
    private boolean draggingScreenVolume;
    private int screenAudioY(){return compactPlayer()?74:143;}
    private boolean screenAudioVisible(){return com.wildfire.client.screens.ScreenAudio.inRange()&&com.wildfire.client.screens.BrowserRuntime.running();}
    private boolean onScreenVolume(double mx,double my){
        int x=Math.max(drawerWidth+8,width-258),w=width-x-8;x+=playerSlideOffset(w);
        return screenAudioVisible()&&w>=140&&closingAt==0&&mx>=x+10&&mx<=x+w-10&&my>=screenAudioY()+14&&my<=screenAudioY()+31;
    }
    private void updateScreenVolume(double mx){
        int x=Math.max(drawerWidth+8,width-258),w=width-x-8;x+=playerSlideOffset(w);
        com.wildfire.client.screens.BrowserRuntime.volume((float)Math.clamp((mx-x-12)/Math.max(1,w-24d),0,1));
    }
    private int playerVolumeY(){return compactPlayer()?46:89;}
    private void renderPlayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = Math.max(drawerWidth + 8, width - 258);
        int panelWidth = width - x - 8;
        if (panelWidth < 140) return;
        x += playerSlideOffset(panelWidth);
        // Match the accent's outer contour: no square background pixels beneath its cut corners.
        if(screenAudioVisible()){
            int y=screenAudioY();
            RoundedPanels.fill(graphics,x,y,x+panelWidth,y+53,0xEC171B25);
            graphics.text(font,font.plainSubstrByWidth("Экран · "+com.wildfire.client.screens.ScreenAudio.site(),panelWidth-24),x+12,y+5,0xFFB9C2CC,false);
            float level=com.wildfire.client.screens.BrowserRuntime.volume();
            int thumb=x+12+Math.round((panelWidth-24)*level);
            graphics.fill(x+12,y+21,x+panelWidth-12,y+24,0xFF343A43);
            graphics.fill(x+12,y+21,thumb,y+24,UiTheme.ACCENT);
            graphics.fill(thumb-2,y+18,thumb+2,y+27,UiTheme.ACCENT);
            String notice=com.wildfire.client.music.MusicPriority.notice();
            String text=notice.isEmpty()?"Громкость экрана: "+Math.round(level*100)+"%":notice;
            graphics.text(font,font.plainSubstrByWidth(text,panelWidth-24),x+12,y+36,0xFFB9A5D8,false);
            if(mouseX>=x&&mouseX<x+panelWidth&&mouseY>=y+32&&mouseY<y+53)
                graphics.setTooltipForNextFrame(Component.literal(text),mouseX,mouseY);
        }else if(!com.wildfire.client.music.MusicPriority.notice().isEmpty()){
            String notice=com.wildfire.client.music.MusicPriority.notice();
            graphics.text(font,font.plainSubstrByWidth(notice,panelWidth-12),x+6,screenAudioY()+4,0xFFB9A5D8,false);
            if(mouseX>=x&&mouseX<x+panelWidth&&mouseY>=screenAudioY()&&mouseY<screenAudioY()+16)
                graphics.setTooltipForNextFrame(Component.literal(notice),mouseX,mouseY);
        }
        int bottom=compactPlayer()?66:135;
        RoundedPanels.fillLeft(graphics, x, 10, x + panelWidth - 1, bottom, 0xEC171B25, 6);
        graphics.fill(x + panelWidth - 1, 11, x + panelWidth, bottom-1, 0xEC171B25);
        RoundedPanels.fillRightBar(graphics, x + panelWidth - 3, 10, x + panelWidth, bottom, MusicClient.accent());
        com.wildfire.client.music.MusicPlatformIcon.draw(graphics, MusicClient.accent(), x + 12, 23);
        graphics.text(font, MusicClient.active() ? "СЕЙЧАС ИГРАЕТ" : "ПЛЕЕР", x + 37, 20, 0xFFB9A5D8, false);
        if (MusicClient.active()) {
            ModUpdate.drawScrollingTitle(graphics, MusicClient.title(), x + 37, 35, panelWidth - 48);
            graphics.text(font, "Трек " + MusicClient.position() + " · " + MusicClient.timeLabel(),
                x + 12, 56, 0xFFB9C2CC, false);
            float progress = draggingMusicSeek ? musicSeekPreview : MusicClient.progress();
            graphics.fill(x + 12, 74, x + panelWidth - 12, 77, 0xFF343A43);
            if (progress >= 0) graphics.fill(x + 12, 74,
                x + 12 + (int) ((panelWidth - 24) * progress), 77, MusicClient.accent());
            if (progress >= 0) drawSeekKnob(graphics, x + 12 + Math.round((panelWidth - 24) * progress), 75,
                MusicClient.canSeek() ? MusicClient.accent() : 0xFF929AA6);
        } else {
            graphics.text(font, "Сейчас ничего не играет", x + (compactPlayer()?37:12), compactPlayer()?32:56, 0xFFB9C2CC, false);
        }
        double volume = musicVolume();
        int volumeY=playerVolumeY();
        graphics.text(font, volume < 0 ? (MusicClient.personalActive() ? "Громкость веб-плеера недоступна" : "Пластинки: PV недоступен")
            : (MusicClient.personalActive() ? "Музыка " : "Пластинки ") + Math.round(volume * 100) + "%", x + 12, volumeY, 0xFFB9C2CC, false);
        if (volume >= 0) {
            int sliderRight = x + panelWidth - 14, sliderLeft = recordVolumeSliderLeft(x, panelWidth);
            graphics.fill(sliderLeft, volumeY+3, sliderRight, volumeY+7, 0xFF343A43);
            int thumb = sliderLeft + (int) Math.round((sliderRight - sliderLeft) * volume);
            graphics.fill(sliderLeft, volumeY+3, thumb, volumeY+7, MusicClient.accent());
            graphics.fill(thumb - 2, volumeY, thumb + 2, volumeY+10, MusicClient.accent());
        }
        if (MusicClient.personalActive()) {
            int center = x + panelWidth / 2;
            int hovered = com.wildfire.client.music.MusicUiButton.hit(mouseX, mouseY, center);
            for (int i = 0; i < 3; i++) {
                com.wildfire.client.music.MusicUiButton.playback(graphics,
                    com.wildfire.client.music.MusicUiButton.controlLeft(center, i), 107,
                    com.wildfire.client.music.MusicUiButton.controlWidth(i), i, MusicClient.paused(),
                    MusicClient.accent(), hovered == i && closingAt == 0,
                    pressedMusicControl == i && System.currentTimeMillis() < musicControlPressedUntil);
            }
        }
    }

    private int recordVolumeSliderLeft(int x, int panelWidth) {
        return x + panelWidth - 14 - Math.min(72, Math.max(24, panelWidth - 130));
    }

    private int playerSlideOffset(int panelWidth) {
        int distance = panelWidth + 8;
        if (closingAt != 0) {
            double t = Math.clamp((System.currentTimeMillis() - closingAt) / 220.0, 0.0, 1.0);
            return playerClosingOffset + (int) Math.round((distance - playerClosingOffset) * t * t);
        }
        double t = Math.clamp((System.currentTimeMillis() - openedAt) / 220.0, 0.0, 1.0);
        return (int) Math.round(distance * (1.0 - t) * (1.0 - t));
    }

    private boolean onRecordVolumeSlider(double mouseX, double mouseY) {
        int x = Math.max(drawerWidth + 8, width - 258);
        int panelWidth = width - x - 8;
        x += playerSlideOffset(panelWidth);
        return panelWidth >= 140 && musicVolume() >= 0
            && mouseX >= recordVolumeSliderLeft(x, panelWidth) - 4 && mouseX <= x + panelWidth - 8
            && mouseY >= playerVolumeY()-5 && mouseY <= playerVolumeY()+14;
    }

    private void updateRecordVolume(double mouseX) {
        int x = Math.max(drawerWidth + 8, width - 258);
        int panelWidth = width - x - 8;
        x += playerSlideOffset(panelWidth);
        int sliderLeft = recordVolumeSliderLeft(x, panelWidth), sliderRight = x + panelWidth - 14;
        double volume = Math.clamp((mouseX - sliderLeft) / Math.max(1, sliderRight - sliderLeft), 0d, 1d);
        setMusicVolume(volume, false);
    }

    @Override public void tick() {
        // Activity heartbeats are throttled, never sent per keystroke.
        updateChatActivity();
        com.wildfire.client.music.CardBalanceClient.refresh();
        if (tab == 2) com.wildfire.client.music.GroupInbox.refresh();
        if (!pendingGroupSend.isEmpty()) {
            var result=com.wildfire.client.music.GroupInbox.result(pendingGroupSend);
            if (result!=null || System.currentTimeMillis()-pendingGroupAt>10000) {
                if ((result==null || !result.ok()) && pendingGroupPartner.equals(conversationPartner) && messageInput!=null && messageInput.getValue().isBlank()) messageInput.setValue(pendingGroupBody);
                pendingGroupSend=""; pendingGroupBody="";
            }
        }
        if (tab == 2) com.wildfire.gui.WebStickers.refresh();
        if (!com.wildfire.client.music.SpringPlusSession.enabled()) {
            Minecraft.getInstance().gui.setScreen(parent);
            return;
        }
        if (closingAt == 0 && tab == 0 && slideOffset() == 0) {
            if (selectedNotice != null) NotificationInbox.markRead(List.of(selectedNotice));
            else {
                var entries = NotificationInbox.entries();
                var layout=notificationLayout(entries);
                NotificationInbox.markRead(entries.subList(layout.first(),layout.end()));
            }
        }
        if (closingAt != 0 && System.currentTimeMillis() - closingAt >= 220)
            Minecraft.getInstance().gui.setScreen(parent);
    }
    @Override public void onClose() {
        reactionPicker = false;
        PrivateMessageInbox.close();
        if (closingAt == 0) {
            int x = Math.max(drawerWidth + 8, width - 258);
            playerClosingOffset = playerSlideOffset(width - x - 8);
            draggingRecordVolume = false;
            closingAt = System.currentTimeMillis();
        }
    }
    private void sendChatActivity(String partner,String state) {
        if(com.wildfire.client.music.GroupInbox.isGroup(partner))
            com.wildfire.client.music.GroupInbox.request("activity",Map.of("group",com.wildfire.client.music.GroupInbox.id(partner),"state",state));
        else PrivateMessageInbox.activity(partner,state);
    }
    private void updateChatActivity() {
        long now=System.currentTimeMillis();
        String partner=closingAt==0&&tab==2&&conversationPartner!=null?conversationPartner:"";
        if(!partner.equals(activityPartner)) {
            if(!activityPartner.isEmpty())sendChatActivity(activityPartner,"idle");
            activityPartner=partner;activityState="idle";lastActivitySent=0;lastMessageEdit=0;
        }
        String state=partner.isEmpty()?"idle":reactionPicker?(stickerPicker?"sticker":"emoji")
            :messageInput!=null&&!messageInput.getValue().isBlank()&&now-lastMessageEdit<4000?"typing":"idle";
        if(!partner.isEmpty()&&(!state.equals(activityState)||!state.equals("idle")&&now-lastActivitySent>=2000)) {
            sendChatActivity(partner,state);activityState=state;lastActivitySent=now;
        }
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if(event.key()==86&&(event.modifiers()&2)!=0&&tab==2&&conversationPartner!=null&&closingAt==0
            &&com.wildfire.gui.ChatImages.hasClipboardImage()) {chooseChatImage(true);return true;}
        if (heldSticker != null) {
            if (event.key() == 256) { heldSticker = null; heldStickerButton = null; stickerPreviewShown = false; }
            return true;
        }
        if (reactionPicker && event.key() == 256) { reactionPicker = false; updateWidgets(); return true; }
        if (tab == 2 && conversationPartner != null && messageInput != null
            && messageInput.isFocused() && (event.key() == 257 || event.key() == 335)) {
            sendPrivateMessage();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(tab==5&&closingAt==0&&townsPanel.scroll(mouseX,mouseY,scrollY))return true;
        if (heldSticker != null) return true;
        if(messageInput!=null&&messageInput.visible&&messageInput.isMouseOver(mouseX,mouseY))
            return messageInput.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
        if (tab == 1 && mouseX >= contentLeft() + slideOffset() - 4
            && mouseX < contentLeft() + slideOffset() + contentWidth() + 9 && mouseY >= 83 && mouseY < height - 73) {
            friendScroll = Math.clamp(friendScroll - (int)Math.round(scrollY * 38), 0, friendMaxScroll(friendRows().size()));
            updateWidgets(); return true;
        }
        if (reactionPicker && tab == 2 && mouseX >= contentLeft() + slideOffset()
            && mouseX < contentLeft() + slideOffset() + contentWidth()
            && mouseY >= reactionPickerTop() && mouseY < height - 75 - inputExpansion()) {
            if(stickerPicker&&mouseY>=height-104-inputExpansion()&&mouseY<height-80-inputExpansion()){
                int visible=Math.min(stickerPacks.size(),Math.max(1,contentWidth()/28));
                stickerPackScroll=Math.clamp(stickerPackScroll-(scrollY>0?1:scrollY<0?-1:0),0,Math.max(0,stickerPackIds().size()-visible));
                updateWidgets();return true;
            }
            int count = stickerPicker ? selectedStickers().size() : selectedEmojis().size();
            if (scrollY != 0) reactionScrollRow = Math.clamp(reactionScrollRow - (scrollY > 0 ? 1 : -1), 0, reactionMaxScroll(count));
            updateWidgets(); return true;
        }
        if(tab==0&&selectedNotice==null&&mouseX>=contentLeft()+slideOffset()-4
            &&mouseX<contentLeft()+slideOffset()+contentWidth()+9){
            var layout=notificationLayout(NotificationInbox.entries());
            if(mouseY>=layout.top()&&mouseY<layout.bottom()){
                notificationScroll=Math.clamp(notificationScroll-(int)Math.round(scrollY*NoticeListLayout.PITCH),0,layout.maxOffset());
                notificationAnchor=null;
                return true;
            }
        }
        if (tab == 2 && mouseX >= slideOffset() && mouseX < slideOffset() + 23) {
            int max = Math.max(0, dialogs().size() - sidebarSlots());
            conversationSidebarScroll = Math.clamp(conversationSidebarScroll + (scrollY < 0 ? 1 : -1), 0, max);
            return true;
        }
        if (tab == 0 && selectedNotice != null && mouseX <= drawerWidth) {
            int max = Math.max(0, noticeLines(contentWidth() - 12).size() - Math.max(1, (height - 160) / 11));
            noticeScroll = Math.clamp(noticeScroll + (scrollY < 0 ? 3 : -3), 0, max);
            return true;
        }
        if (tab == 2 && conversationPartner != null && mouseX <= drawerWidth) {
            int max = Math.max(0, conversationContentHeight(contentWidth()) - conversationViewportHeight());
            conversationScroll = Math.clamp(conversationScroll + (scrollY > 0 ? 33 : -33), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if(click.button()==0&&onScreenVolume(click.x(),click.y())){draggingScreenVolume=true;setDragging(true);updateScreenVolume(click.x());return true;}
        if(closingAt==0&&tab==2&&conversationPartner!=null&&!groupConversation()&&!reactionPicker
                &&click.button()==0&&click.y()>=35&&click.y()<71
                &&click.x()>=slideOffset()+23&&click.x()<slideOffset()+drawerWidth-2){
            openContactProfile();return true;
        }
        if(tab==5&&closingAt==0&&townsPanel.click(click.x(),click.y(),click.button()))return true;
        if (heldSticker != null) return true;
        if(closingAt==0&&tab==2&&!reactionPicker&&conversationPartner!=null&&click.button()==0){
            String image=conversationImageAt(click.x(),click.y());
            if(image!=null){attachmentDraft=messageInput==null?"":messageInput.getValue();com.wildfire.gui.ChatImages.view(this,image);return true;}
        }
        if (closingAt == 0 && tab == 2 && click.button() == 0) {
            if (reactionPicker && stickerPicker) {
                for (int slot = 0; slot < reactionButtons.size(); slot++) {
                    Button button = reactionButtons.get(slot);
                    int index = reactionScrollRow * reactionColumns() + slot;
                    if (button.isMouseOver(click.x(), click.y()) && index < selectedStickers().size()) {
                        heldSticker = selectedStickers().get(index); heldStickerButton = button;
                        returningSticker=null;
                        stickerPreviewOriginX=button.getX()+button.getWidth()/2d;
                        stickerPreviewOriginY=button.getY()+button.getHeight()/2d;
                        stickerPressedAt = System.nanoTime(); stickerPreviewShown = false;
                        return true;
                    }
                }
            } else if (!reactionPicker && conversationPartner != null) {
                heldSticker = conversationStickerAt(click.x(), click.y());
                if (heldSticker != null) {
                    returningSticker=null;
                    stickerPreviewOriginX=click.x();stickerPreviewOriginY=click.y();
                    heldStickerButton = null; stickerPressedAt = System.nanoTime(); stickerPreviewShown = false;
                    return true;
                }
            }
        }
        if(closingAt==0&&reactionPicker&&tab==2&&click.button()==1){
            for(int slot=0;slot<reactionButtons.size();slot++)if(reactionButtons.get(slot).isMouseOver(click.x(),click.y())){
                int index=reactionScrollRow*reactionColumns()+slot;
                if(stickerPicker&&index<selectedStickers().size())com.wildfire.gui.ReactionHistory.get().toggle("sticker",selectedStickers().get(index).id());
                else if(!stickerPicker&&index<selectedEmojis().size())com.wildfire.gui.ReactionHistory.get().toggle("emoji",selectedEmojis().get(index).id());
                updateWidgets();return true;
            }
        }
        if(closingAt==0&&tab==2&&!reactionPicker&&groupConversation()&&click.button()==1
            &&com.wildfire.client.music.GroupInbox.allowed(groupId(),8)) {
            long message=groupMessageAt(click.x(),click.y());
            if(message>0){String id=groupId();
                minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed->{
                    minecraft.gui.setScreen(this);
                    if(confirmed)com.wildfire.client.music.GroupInbox.request("delete_message",Map.of("group",id,"message",message));
                },Component.literal("Удалить сообщение?"),Component.literal("Сообщение будет удалено у всех участников группы.")));
                return true;
            }
        }
        if(closingAt==0 && tab==2 && !reactionPicker && conversationPartner!=null && click.button()==0) {
            var link=conversationLinkAt(click.x(),click.y());
            if(link!=null){net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this,link,false);return true;}
        }
        if (click.button() == 0 && onMusicSeek(click.x(), click.y())) {
            setDragging(true);
            draggingMusicSeek = true; musicSeekTrack = MusicClient.position() + "\u0000" + MusicClient.title();
            previewMusicSeek(click.x()); return true;
        }
        if (click.button() == 0 && closingAt == 0 && MusicClient.personalActive()) {
            int playerX = Math.max(drawerWidth + 8, width - 258);
            int playerWidth = width - playerX - 8;
            playerX += playerSlideOffset(playerWidth);
            int index = com.wildfire.client.music.MusicUiButton.hit(click.x(), click.y(), playerX + playerWidth / 2);
            if (playerWidth >= 140 && index >= 0) {
                pressedMusicControl = index; musicControlPressedUntil = System.currentTimeMillis() + 140;
                MusicClient.control(index); return true;
            }
        }
        if (closingAt != 0) return true;
        if (click.button() == 0 && onRecordVolumeSlider(click.x(), click.y())) {
            setDragging(true);
            draggingRecordVolume = true;
            updateRecordVolume(click.x());
            return true;
        }
        if (tab == 2 && click.x() >= slideOffset() && click.x() < slideOffset() + 23) {
            if (System.currentTimeMillis() - sidebarTransitionAt < 180) return true;
            if (click.y() >= 40 && click.y() < 59) { setTab(tabBeforeChats); return true; }
            if (click.x() >= slideOffset()+2 && click.x() < slideOffset()+20 && click.y() >= 64 && click.y() < 82) {
                if(com.wildfire.client.music.GroupInbox.supported()) minecraft.gui.setScreen(
                    new com.wildfire.client.music.GroupEditorScreen(this,"",groupFriends(),this::openGroupConversation));
                return true;
            }
            int row = ((int) click.y() - 85) / 21;
            if (click.y() >= 85 && row >= 0 && row < sidebarSlots()) {
                int index = conversationSidebarScroll + row;
                var conversations = dialogs();
                if (index < conversations.size()) openConversation(conversations.get(index).partner());
                return true;
            }
        }
        if (tab == 0 && selectedNotice == null
            && click.x() >= contentLeft() + slideOffset() - 4 && click.x() < contentLeft() + slideOffset() + contentWidth() + 4) {
            var entries=NotificationInbox.entries();
            int index=notificationLayout(entries).hit(click.y());
            if(index>=0){
                    var entry = entries.get(index);
                    if (entry.category().equals("ЛС")) { openConversation(entry.title()); return true; }
                    selectedNotice = entry;
                    noticeScroll = 0;
                    return true;
            }
        }
        var suggestions = friendSuggestions();
        int suggestionX = contentLeft() + slideOffset(), suggestionWidth = contentWidth() - 94;
        for (int i = 0; i < suggestions.size(); i++) {
            int y = friendName.getY() - 18 * (suggestions.size() - i);
            if (click.x() >= suggestionX && click.x() < suggestionX + suggestionWidth
                && click.y() >= y && click.y() < y + 18) {
                friendName.setValue(suggestions.get(i));
                friendName.setFocused(true);
                return true;
            }
        }
        if (click.x() > slideOffset() + drawerWidth) {
            onClose();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private String conversationImageAt(double mouseX,double mouseY){
        int left=contentLeft()+slideOffset(),available=contentWidth(),top=74;
        if(mouseX<left||mouseX>=left+available||mouseY<top||mouseY>=top+conversationViewportHeight())return null;
        var bubbles=conversationBubbles(available);int total=bubbles.stream().mapToInt(b->b.height()+6).sum();
        int y=top-Math.max(0,total-conversationViewportHeight())+conversationScroll;
        for(var b:bubbles){int x=b.outgoing()?left+available-b.width()-4:left+4;
            if(b.image()!=null&&mouseX>=x+8&&mouseX<x+b.width()-8&&mouseY>=y+6&&mouseY<y+b.height()-12)return b.image();y+=b.height()+6;
        }return null;
    }
    private com.wildfire.gui.WebStickers.Sticker conversationStickerAt(double mouseX, double mouseY) {
        int left = contentLeft() + slideOffset(), available = contentWidth(), top = 74;
        if (mouseX < left || mouseX >= left + available || mouseY < top || mouseY >= top + conversationViewportHeight()) return null;
        var bubbles = conversationBubbles(available);
        int total = bubbles.stream().mapToInt(b -> b.height() + 6).sum();
        int y = top - Math.max(0, total - conversationViewportHeight()) + conversationScroll;
        for (var bubble : bubbles) {
            int x = bubble.outgoing() ? left + available - bubble.width() - 4 : left + 4;
            int imageY = y + 6 + (bubble.sender().isEmpty() ? 0 : 11), size = bubble.width() - 16;
            if (bubble.webSticker() != null && mouseX >= x + 8 && mouseX < x + 8 + size
                && mouseY >= imageY && mouseY < imageY + size) return bubble.webSticker();
            y += bubble.height() + 6;
        }
        return null;
    }
    private java.net.URI conversationLinkAt(double mouseX,double mouseY) {
        int left=contentLeft()+slideOffset(),available=contentWidth(),top=74,bottom=top+conversationViewportHeight();
        if(mouseX<left||mouseX>=left+available||mouseY<top||mouseY>=bottom)return null;
        var bubbles=conversationBubbles(available);
        int total=bubbles.stream().mapToInt(b->b.height()+6).sum();
        int y=top-Math.max(0,total-conversationViewportHeight())+conversationScroll;
        for(var bubble:bubbles){
            int x=bubble.outgoing()?left+available-bubble.width()-4:left+4;
            int lineY=y+7+(bubble.sender().isEmpty()?0:11);
            for(var line:bubble.lines()){
                if(mouseY>=lineY&&mouseY<lineY+9&&mouseX>=x+8&&mouseX<x+8+font.width(line)){
                    double target=mouseX-x-8;float[] cursor={0};java.net.URI[] found={null};
                    line.accept((index,style,codepoint)->{
                        int width=font.width(Component.literal(new String(Character.toChars(codepoint))).setStyle(style));
                        if(target>=cursor[0]&&target<cursor[0]+width){
                            if(style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.OpenUrl url)found[0]=url.uri();
                            return false;
                        }
                        cursor[0]+=width;return true;
                    });
                    return found[0];
                }
                lineY+=11;
            }
            y+=bubble.height()+6;
        }
        return null;
    }
    private long groupMessageAt(double mouseX,double mouseY) {
        int left=contentLeft()+slideOffset(),available=contentWidth(),top=74,bottom=top+conversationViewportHeight();
        if(mouseX<left||mouseX>=left+available||mouseY<top||mouseY>=bottom)return -1;
        var bubbles=conversationBubbles(available);var messages=com.wildfire.client.music.GroupInbox.messages(groupId());
        int total=bubbles.stream().mapToInt(b->b.height()+6).sum();
        int y=top-Math.max(0,total-conversationViewportHeight())+conversationScroll;
        for(int i=0;i<bubbles.size()&&i<messages.size();i++){var b=bubbles.get(i);int x=b.outgoing()?left+available-b.width()-4:left+4;
            if(mouseX>=x&&mouseX<x+b.width()&&mouseY>=y&&mouseY<y+b.height())return messages.get(i).id();
            y+=b.height()+6;
        }
        return -1;
    }
    @Override public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if(draggingScreenVolume){updateScreenVolume(click.x());return true;}
        if (draggingMusicSeek) { previewMusicSeek(click.x()); return true; }
        if (draggingRecordVolume) { updateRecordVolume(click.x()); return true; }
        return super.mouseDragged(click, dragX, dragY);
    }
    @Override public boolean mouseReleased(MouseButtonEvent click) {
        if(draggingScreenVolume){draggingScreenVolume=false;setDragging(false);com.wildfire.client.music.AudioPreferences.save();return true;}
        if (heldSticker != null && click.button() == 0) {
            Button button = heldStickerButton;
            var clickedSticker=button==null&&!stickerPreviewShown&&System.nanoTime()-stickerPressedAt<1_000_000_000L
                &&closingAt==0&&tab==2&&!reactionPicker?conversationStickerAt(click.x(),click.y()):null;
            boolean openPack=clickedSticker!=null&&clickedSticker.id().equals(heldSticker.id());
            boolean send = !stickerPreviewShown && System.nanoTime() - stickerPressedAt < 1_000_000_000L
                && closingAt == 0 && tab == 2 && reactionPicker && stickerPicker
                && button != null && button.isMouseOver(click.x(), click.y());
            returnStickerPreview();
            heldSticker = null; heldStickerButton = null; stickerPreviewShown = false;
            if (send) button.onPress(click);
            else if(openPack){attachmentDraft=messageInput==null?"":messageInput.getValue();minecraft.gui.setScreen(new com.wildfire.gui.StickerShopScreen(this,clickedSticker));}
            return true;
        }
        if (draggingMusicSeek && click.button() == 0) {
            setDragging(false);
            previewMusicSeek(click.x()); draggingMusicSeek = false;
            if (closingAt == 0 && musicSeekTrack.equals(MusicClient.position() + "\u0000" + MusicClient.title()))
                MusicClient.seek(musicSeekPreview);
            return true;
        }
        if (draggingRecordVolume) {
            setDragging(false);
            draggingRecordVolume = false;
            double volume = musicVolume();
            if (volume >= 0) setMusicVolume(volume, true);
            return true;
        }
        return super.mouseReleased(click);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        if(!activityPartner.isEmpty())sendChatActivity(activityPartner,"idle");
        activityPartner="";activityState="idle";
        heldSticker = null; heldStickerButton = null; stickerPreviewShown = false;
        super.removed();
    }

    private final class FriendIconButton extends Button {
        private final int row;
        private final boolean message;

        private FriendIconButton(int row, boolean message, int x, int y, OnPress action) {
            super(x, y, 22, 20, Component.empty(), action, DEFAULT_NARRATION);
            this.row = row;
            this.message = message;
        }

        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,com.wildfire.gui.UiTheme.ACCENT);
            var rows = friendRows();
            int index = friendScroll / 38 + row;
            if (index >= rows.size()) return;
            String kind = rows.get(index).kind();
            if (!message && "incoming".equals(kind)) {
                for (int i = 0; i < 4; i++) graphics.fill(getX()+5+i,getY()+9+i,getX()+7+i,getY()+11+i,0xFF36CF79);
                for (int i = 0; i < 8; i++) graphics.fill(getX()+8+i,getY()+12-i,getX()+10+i,getY()+14-i,0xFF36CF79);
                return;
            }
            ItemStack icon = new ItemStack(message
                ? ("incoming".equals(kind) ? Items.BARRIER : Items.WRITABLE_BOOK)
                : Items.BARRIER);
            graphics.item(icon, getX() + 3, getY() + 2);
            if (!message && rows.get(index).name().equalsIgnoreCase(confirmRemoval)
                && System.currentTimeMillis() < confirmRemovalUntil)
                RoundedPanels.fillBar(graphics, getX() + 2, getY() + 17, getX() + 20, getY() + 20, 0xFFE13251);
        }
        @Override public boolean isMouseOver(double x, double y) { return visible && super.isMouseOver(x,y); }
    }

    private final class IconTabButton extends Button {
        private final int index;
        private final ItemStack icon;

        private IconTabButton(int index, ItemStack icon, int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), button -> setTab(index), DEFAULT_NARRATION);
            this.index = index;
            this.icon = icon;
        }

        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,tab==index||isHoveredOrFocused(),false,ICON_COLORS[index]);
            if (index == 5) {
                graphics.pose().pushMatrix();graphics.pose().translate(getX()+3,getY()+3);graphics.pose().scale(.6f,.6f);
                com.wildfire.gui.TownsPanel.icon(graphics,0,0);graphics.pose().popMatrix();
            } else if (index == 1 && minecraft.player != null)
                PlayerFaceExtractor.extractRenderState(graphics, minecraft.player.getSkin(), getX() + 3, getY() + 3, 12);
            else {
                graphics.pose().pushMatrix();
                graphics.pose().translate(getX() + 3, getY() + 3);
                graphics.pose().scale(0.75f, 0.75f);
                graphics.item(icon, 0, 0);
                graphics.pose().popMatrix();
            }
            if (index == 0 && NotificationInbox.unread() > 0)
                badge(graphics, NotificationInbox.unread() > 99 ? "99+" : Integer.toString(NotificationInbox.unread()), 0xFFE13251);
            if (index == 1) {
                long incoming = SocialClient.rows().stream().filter(row -> "incoming".equals(row.kind())).count();
                long online = SocialClient.rows().stream().filter(row -> "friend".equals(row.kind()) && "in_game".equals(row.status())).count();
                if (incoming > 0) badge(graphics, incoming > 99 ? "99+" : Long.toString(incoming), 0xFFE13251);
                else if (online > 0) badge(graphics, online > 99 ? "99+" : Long.toString(online), 0xFF35B978);
            }
            if (index == 2 && PrivateMessageInbox.unread() > 0)
                badge(graphics, PrivateMessageInbox.unread() > 99 ? "99+" : Integer.toString(PrivateMessageInbox.unread()), 0xFFE13251);
        }

        private void badge(GuiGraphicsExtractor graphics, String label, int color) {
            int centerX = getX() + getWidth() - 2, centerY = getY() + 4;
            badgeCircle(graphics, centerX, centerY, color);
            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY);
            float scale = Math.min(0.5f, 7f / Math.max(1, font.width(label)));
            graphics.pose().scale(scale, scale);
            graphics.text(font, label, -font.width(label) / 2, -4, 0xFFFFFFFF, false);
            graphics.pose().popMatrix();
        }

        private void badgeCircle(GuiGraphicsExtractor graphics, int x, int y, int color) {
            int[] halfWidths = {1, 2, 3, 4, 4, 4, 3, 2, 1};
            for (int row = 0; row < halfWidths.length; row++)
                graphics.fill(x - halfWidths[row], y + row - 4, x + halfWidths[row] + 1, y + row - 3, color);
        }
    }

    private static double musicVolume() {
        if (MusicClient.spotifyActive()) return com.wildfire.client.music.SpotifyWebPlayer.view().volume() / 100d;
        return MusicClient.personalActive() ? com.wildfire.client.music.PersonalMusicPlayer.view().volume() / 100d : DiscVoiceVolume.get();
    }
    private static void setMusicVolume(double value, boolean persist) {
        if (MusicClient.spotifyActive()) { com.wildfire.client.music.SpotifyWebPlayer.volume((int) Math.round(value * 100)); return; }
        if (MusicClient.personalActive()) com.wildfire.client.music.PersonalMusicPlayer.volume((int) Math.round(value * 100));
        else DiscVoiceVolume.set(value, persist);
    }
}
