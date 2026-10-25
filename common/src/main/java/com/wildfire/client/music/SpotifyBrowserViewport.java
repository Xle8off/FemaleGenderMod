package com.wildfire.client.music;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Windows-only child viewport. Only windows belonging to our launched process are eligible. */
final class SpotifyBrowserViewport {
    public interface Win extends StdCallLibrary {
        interface Visitor extends StdCallCallback { boolean invoke(Pointer hwnd, Pointer data); }
        boolean EnumWindows(Visitor visitor, Pointer data);
        int GetWindowThreadProcessId(Pointer hwnd, IntByReference pid);
        boolean IsWindowVisible(Pointer hwnd);
        int GetWindowLongW(Pointer hwnd, int index);
        int SetWindowLongW(Pointer hwnd, int index, int value);
        Pointer SetParent(Pointer child, Pointer parent);
        Pointer GetParent(Pointer child);
        boolean SetWindowPos(Pointer hwnd, Pointer after, int x, int y, int w, int h, int flags);
        boolean ShowWindow(Pointer hwnd, int mode);
        boolean IsWindow(Pointer hwnd);
        boolean GetClientRect(Pointer hwnd, Pointer rect);
        int SetWindowRgn(Pointer hwnd, Pointer region, boolean redraw);
        Pointer GetForegroundWindow();
        Pointer SetFocus(Pointer hwnd);
        boolean IsChild(Pointer parent, Pointer hwnd);
        boolean AttachThreadInput(int from, int to, boolean attach);
        boolean GetCursorPos(Pointer point);
        boolean ScreenToClient(Pointer hwnd, Pointer point);
        short GetAsyncKeyState(int key);
        boolean GetGUIThreadInfo(int thread, Pointer info);
        int GetClassNameW(Pointer hwnd, char[] name, int count);
        int GetWindowTextW(Pointer hwnd, char[] name, int count);
    }
    public interface Kernel extends StdCallLibrary { int GetCurrentThreadId(); }
    public interface Gdi extends StdCallLibrary {
        Pointer CreateRectRgn(int left, int top, int right, int bottom);
        boolean DeleteObject(Pointer region);
    }
    private static Win api;
    private static Gdi gdi;
    private static Kernel kernel;
    private static boolean mouseDown;
    private static int titleInset;
    private static long checkedAt;
    private static volatile Pointer child;
    private static Pointer mainChild;
    private static int mainInset;
    private static volatile long parent;
    private static volatile int x, y, width, height;
    private static volatile boolean visible;
    private static volatile boolean active;
    static boolean supported() { return System.getProperty("os.name", "").startsWith("Windows"); }
    /** Window inventory only, restricted to the browser process launched for music login. */
    static boolean loginWindowVisible(Process browser) {
        if (!supported() || browser == null) return false;
        return loginWindowVisible(java.util.List.of(browser.toHandle()));
    }
    static boolean loginWindowVisible(java.util.List<ProcessHandle> browsers) {
        if (!supported()) return false;
        Win windows = Native.load("user32", Win.class);
        Set<Long> owned = new HashSet<>();
        for (var browser : browsers) {
            owned.add(browser.pid()); browser.descendants().forEach(p -> owned.add(p.pid()));
        }
        boolean[] visible = {false};
        windows.EnumWindows((hwnd, data) -> {
            IntByReference pid = new IntByReference(); windows.GetWindowThreadProcessId(hwnd, pid);
            if (owned.contains(Integer.toUnsignedLong(pid.getValue())) && windows.IsWindowVisible(hwnd)) {
                char[] clazz = new char[128]; windows.GetClassNameW(hwnd, clazz, clazz.length);
                if (Native.toString(clazz).startsWith("Chrome_WidgetWin")) { visible[0] = true; return false; }
            }
            return true;
        }, null);
        return visible[0];
    }
    static void layout(long hwnd, int left, int top, int w, int h, boolean show) {
        active = show;
        if (parent == hwnd && x == left && y == top && width == Math.max(1, w) && height == Math.max(1, h) && visible == show) {
            // Chromium may restore its top-level decoration after startup or a DPI change.
            if (child != null && System.nanoTime() - checkedAt > 500_000_000L) {
                checkedAt = System.nanoTime();
                int style = api.GetWindowLongW(child, -16);
                if (style != childStyle(style)) apply();
            }
            return;
        }
        parent = hwnd; x = left; y = top; width = Math.max(1, w); height = Math.max(1, h); visible = show;
        if (child != null) apply();
    }
    static void attach(Process process) throws IOException {
        if (!supported() || parent == 0) throw new IOException("No game viewport");
        api = Native.load("user32", Win.class);
        gdi = Native.load("gdi32", Gdi.class);
        kernel = Native.load("kernel32", Kernel.class);
        titleInset = 0;
        Pointer[] found = {null};
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        do {
            Set<Long> owned = new HashSet<>(); owned.add(process.pid());
            process.descendants().forEach(p -> owned.add(p.pid()));
            api.EnumWindows((hwnd, data) -> {
                IntByReference pid = new IntByReference(); api.GetWindowThreadProcessId(hwnd, pid);
                if (owned.contains(Integer.toUnsignedLong(pid.getValue())) && api.IsWindowVisible(hwnd)) {
                    char[] clazz = new char[128]; api.GetClassNameW(hwnd, clazz, clazz.length);
                    if (Native.toString(clazz).startsWith("Chrome_WidgetWin")) { found[0] = hwnd; return false; }
                }
                return true;
            }, null);
            if (found[0] == null) {
                try { Thread.sleep(100); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new IOException("Browser window wait interrupted", interrupted);
                }
            }
        } while (found[0] == null && System.nanoTime() < deadline);
        if (found[0] == null) throw new IOException("Owned browser window unavailable after 5 seconds; processAlive=" + process.isAlive());
        Pointer window = found[0], game = new Pointer(parent);
        int style = api.GetWindowLongW(window, -16);
        api.SetWindowLongW(window, -16, childStyle(style));
        api.SetParent(window, game);
        if (!game.equals(api.GetParent(window))) {
            api.SetWindowLongW(window, -16, style);
            throw new IOException("Cannot attach browser to game");
        }
        child = window;
        mainChild = window;
        apply();
    }
    static synchronized boolean popup(Process process, String title) {
        if (process == null || mainChild == null || api == null) return false;
        Set<Long> owned = new HashSet<>(); owned.add(process.pid());
        process.descendants().forEach(p -> owned.add(p.pid()));
        java.util.List<Pointer> candidates = new java.util.ArrayList<>();
        api.EnumWindows((hwnd, data) -> {
            IntByReference pid = new IntByReference(); api.GetWindowThreadProcessId(hwnd, pid);
            if (!owned.contains(Integer.toUnsignedLong(pid.getValue())) || !api.IsWindowVisible(hwnd)) return true;
            char[] clazz = new char[128]; api.GetClassNameW(hwnd, clazz, clazz.length);
            if (!Native.toString(clazz).startsWith("Chrome_WidgetWin")) return true;
            char[] text = new char[512]; api.GetWindowTextW(hwnd, text, text.length);
            if (!title.isBlank() && !Native.toString(text).contains(title)) return true;
            candidates.add(hwnd); return true;
        }, null);
        if (candidates.size() != 1) return false;
        Pointer window = candidates.getFirst(), game = new Pointer(parent);
        int style = api.GetWindowLongW(window, -16);
        api.SetWindowLongW(window, -16, childStyle(style));
        api.SetParent(window, game);
        if (!game.equals(api.GetParent(window))) { api.SetWindowLongW(window, -16, style); return false; }
        if (child.equals(mainChild)) mainInset = titleInset;
        api.ShowWindow(child, 0);
        child = window;
        // Keep the OAuth window's own address UI visible; never crop a login page using music dimensions.
        titleInset = 0; apply(); return true;
    }
    static synchronized void returnToMusic() {
        if (mainChild == null || api == null) return;
        if (mainChild.equals(child)) { apply(); return; }
        if (child != null && !child.equals(mainChild) && api.IsWindow(child)) api.ShowWindow(child, 0);
        child = mainChild; titleInset = mainInset; apply();
    }
    private static synchronized void apply() {
        if (child == null || api == null || !api.IsWindow(child)) return;
        int style = api.GetWindowLongW(child, -16);
        if (style != childStyle(style)) api.SetWindowLongW(child, -16, childStyle(style));
        // Chrome also draws an app title inside its client area. Clip that measured strip,
        // shifting the browser up and enlarging it so the website still fills the viewport.
        api.SetWindowPos(child, null, x, y - titleInset, width, height + titleInset, 0x0010 | 0x0020 | 0x0004);
        Pointer region = gdi.CreateRectRgn(0, titleInset, width, height + titleInset);
        if (region != null && api.SetWindowRgn(child, region, true) == 0) gdi.DeleteObject(region);
        // Successful SetWindowRgn transfers region ownership to Windows.
        api.ShowWindow(child, visible ? 5 : 0);
    }
    static int childStyle(int style) { return (style & ~0x80CF0000) | 0x40000000; }
    static int measuredInset(int clientHeight, double pagePhysicalHeight, int previous) {
        if (!Double.isFinite(pagePhysicalHeight) || pagePhysicalHeight <= 0) return previous;
        int gap = (int) Math.round(clientHeight - pagePhysicalHeight);
        return gap >= 0 && gap <= 128 ? gap : previous;
    }
    static synchronized void measure(double pagePhysicalHeight) {
        if (child == null || api == null || !api.IsWindow(child) || !child.equals(mainChild)) return;
        try (Memory rect = new Memory(16)) {
            if (!api.GetClientRect(child, rect)) return;
            int next = measuredInset(rect.getInt(12) - rect.getInt(4), pagePhysicalHeight, titleInset);
            if (next != titleInset) { titleInset = next; apply(); }
        }
    }
    static void show(boolean show) { visible = show && active; apply(); }
    /** Called on Minecraft's UI thread. No key forwarding, hooks or credential reading. */
    static synchronized void focusOnClick() {
        if (!active || !visible || child == null || api == null || !api.IsWindow(child)) { mouseDown = false; return; }
        Pointer game = new Pointer(parent), foreground = api.GetForegroundWindow();
        if (foreground == null || !(game.equals(foreground) || child.equals(foreground) || api.IsChild(game, foreground))) {
            mouseDown = false; return;
        }
        boolean down = (api.GetAsyncKeyState(1) & 0x8000) != 0;
        boolean pressed = down && !mouseDown; mouseDown = down;
        if (!pressed) return;
        try (Memory point = new Memory(8)) {
            if (!api.GetCursorPos(point) || !api.ScreenToClient(game, point)) return;
            if (!inside(point.getInt(0), point.getInt(4), x, y, width, height)) return;
        }
        int browserThread = api.GetWindowThreadProcessId(child, new IntByReference());
        int currentThread = kernel.GetCurrentThreadId();
        boolean attached = browserThread != currentThread && api.AttachThreadInput(currentThread, browserThread, true);
        if (browserThread != currentThread && !attached) return;
        try {
            // Preserve Chrome's renderer focus when the click already selected a text field.
            int size = 8 + 6 * Native.POINTER_SIZE + 16;
            try (Memory info = new Memory(size)) {
                info.clear(); info.setInt(0, size);
                Pointer destination = child;
                if (api.GetGUIThreadInfo(browserThread, info)) {
                    Pointer focused = info.getPointer(8 + Native.POINTER_SIZE);
                    if (focused != null && api.IsChild(child, focused)) destination = focused;
                }
                api.SetFocus(destination);
            }
        } finally {
            if (attached) api.AttachThreadInput(currentThread, browserThread, false);
        }
    }
    static boolean inside(int px, int py, int left, int top, int w, int h) {
        return px >= left && py >= top && px - left < w && py - top < h;
    }
    static synchronized void deactivate() {
        active = false;
        // Return focus before hiding the child. Do not steal focus after an Alt-Tab.
        if (api != null && kernel != null && child != null && parent != 0) {
            Pointer game = new Pointer(parent), foreground = api.GetForegroundWindow();
            if (game.equals(foreground) || child.equals(foreground) ||
                (foreground != null && api.IsChild(game, foreground))) {
                int thread = api.GetWindowThreadProcessId(child, new IntByReference());
                int current = kernel.GetCurrentThreadId();
                boolean attached = thread != current && api.AttachThreadInput(current, thread, true);
                try { if (thread == current || attached) api.SetFocus(game); }
                finally { if (attached) api.AttachThreadInput(current, thread, false); }
            }
        }
        mouseDown = false;
        show(false);
    }
    static synchronized void clear() { child = mainChild = null; titleInset = mainInset = 0; mouseDown = false; }
}
