package com.wildfire.client.music;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


/** Browser sites, each with an isolated persistent profile. */
public final class SpotifyEmbeddedScreen extends Screen {
    private final Screen parent;
    private final MusicWebsite site;
    private static MusicWebsite lastSite = MusicWebsite.SPOTIFY;
    static MusicWebsite lastSite() { return lastSite; }
    static boolean lastYandex() { return lastSite == MusicWebsite.YANDEX; }
    public SpotifyEmbeddedScreen(Screen parent) { this(parent, MusicWebsite.SPOTIFY); }
    public SpotifyEmbeddedScreen(Screen parent, boolean yandex) { this(parent, yandex ? MusicWebsite.YANDEX : MusicWebsite.SPOTIFY); }
    public SpotifyEmbeddedScreen(Screen parent, MusicWebsite site) {
        super(Component.literal(site.label)); this.parent=parent; this.site=site;
    }
    private MusicUiButton authReturn;
    private MusicUiButton externalLogin, restartPlayer;
    private int headerHeight;
    private int left, top, panelWidth, panelHeight;
    @Override protected void init() {
        lastSite=site;
        panelWidth=Math.min(width-12,Math.max(280,(int)(width*.88)));
        panelHeight=Math.min(height-12,Math.max(160,(int)(height*.86)));
        left=(width-panelWidth)/2; top=(height-panelHeight)/2;
        headerHeight = 56;
        addRenderableWidget(new MusicUiButton(left+6,top+5,48,"Назад",0xFFB681D4,false,b -> onClose()));
        externalLogin=addRenderableWidget(new MusicUiButton(left+58,top+5,100,"Вход в браузере",site.accent,false,b -> {
            SpotifyBrowserViewport.deactivate();
            SpotifyWebPlayer.loginInBrowser(minecraft.gameDirectory.toPath(),site);
        }));
        restartPlayer=addRenderableWidget(new MusicUiButton(left+162,top+5,100,"Вернуть плеер",site.accent,false,b ->
            SpotifyWebPlayer.start(minecraft.gameDirectory.toPath(),site)));
        int start = left+6, y = top+30;
        int w = 28;
        int index=0;
        for (MusicWebsite provider : MusicWebsite.values()) {
            var button=addRenderableWidget(new MusicUiButton(start+index*(w+2),y,w,provider.label,provider.accent,
                site==provider,b -> minecraft.gui.setScreen(new SpotifyEmbeddedScreen(parent,provider))).providerIcon());
            button.active=provider!=site; index++;
        }
        authReturn=addRenderableWidget(new MusicUiButton(left+6,top+headerHeight,78,"К музыке",site.accent,false,b -> SpotifyWebPlayer.cancelAuth()));
        authReturn.visible=false;
        if(!SpotifyBrowserViewport.supported())return;
        layout(); SpotifyWebPlayer.start(minecraft.gameDirectory.toPath(),site);
    }
    private void layout() {
        var window=minecraft.getWindow();
        double sx=(double)window.getScreenWidth()/Math.max(1,width), sy=(double)window.getScreenHeight()/Math.max(1,height);
        int inset=headerHeight+(authReturn!=null&&authReturn.visible?26:0);
        SpotifyBrowserViewport.layout(org.lwjgl.sdl.SDLProperties.SDL_GetPointerProperty(org.lwjgl.sdl.SDLVideo.SDL_GetWindowProperties(window.handle()),"SDL.window.win32.hwnd",0L),(int)Math.round((left+4)*sx),(int)Math.round((top+inset)*sy),
            (int)Math.round((panelWidth-8)*sx),(int)Math.round(Math.max(1,panelHeight-inset-4)*sy),true);
    }
    @Override public void tick() {
        if(externalLogin!=null)externalLogin.active=!SpotifyWebPlayer.externalLoginOpen();
        if(restartPlayer!=null)restartPlayer.active=!SpotifyWebPlayer.externalLoginOpen();
        if(authReturn!=null)authReturn.visible=!SpotifyWebPlayer.authHost().isEmpty();
        if(SpotifyBrowserViewport.supported()){layout();SpotifyBrowserViewport.focusOnClick();}
    }
    @Override public void removed(){SpotifyBrowserViewport.deactivate();}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        extractTransparentBackground(g);
        g.fill(0,0,width,height,0x88000000);
        com.wildfire.gui.FlatButtons.plate(g,left,top,panelWidth,panelHeight,true,false,false,site.accent);
        super.extractRenderState(g,mouseX,mouseY,delta);
        var state = SpotifyWebPlayer.view();
        if (SpotifyBrowserViewport.supported() && (!state.running() || SpotifyWebPlayer.externalLoginOpen())) {
            String message = SpotifyWebPlayer.externalLoginOpen()
                ? "Войдите в обычном окне браузера и закройте его. Плеер вернётся автоматически."
                : state.status();
            int row = top+headerHeight + 18;
            for (var line : font.split(Component.literal(message), Math.max(20, panelWidth - 24))) {
                g.text(font, line, left+12, row, 0xFFE8EDF3, false); row += 12;
            }
        }
        if(!SpotifyBrowserViewport.supported())g.text(font,"Только Windows",left+12,top+headerHeight+8,0xFFE8EDF3,false);
    }
}
