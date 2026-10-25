package dev.aeltorx.inflow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ModerationService {
    public record Target(UUID id, String name) { }
    private static long lastSubmission;
    private record Pending(Target target, List<String> commands, Object connection, long due) { }
    private static Pending pending;
    private static Object remoteConnection;
    private static long remoteUntil;
    private ModerationService() { }
    public static boolean allowed() {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.getConnection() != null
                && (mc.player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)||ModerationWorkspace.ready());
    }
    public static List<Target> players() {
        return playersOnline();
    }
    /** Uses the normal visible crosshair hit only, never traces through blocks. */
    public static Target crosshairTarget() {
        var mc=Minecraft.getInstance();
        if(!(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)||!(hit.getEntity() instanceof net.minecraft.world.entity.player.Player player)||player==mc.player)return null;
        return playersOnline().stream().filter(p->p.id().equals(player.getUUID())).findFirst().orElse(null);
    }
    private static List<Target> playersOnline() {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return List.of();
        return mc.getConnection().getOnlinePlayers().stream()
            .filter(p -> mc.player == null || !p.getProfile().id().equals(mc.player.getUUID()))
            .map(p -> new Target(p.getProfile().id(), p.getProfile().name()))
            .filter(p -> p.name().matches("[A-Za-z0-9_]{1,16}"))
            .sorted(java.util.Comparator.comparing(Target::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public static boolean online(Target target) {
        return target != null && players().stream().anyMatch(p -> p.equals(target));
    }
    public static boolean commandAvailable(String command) {
        var mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().getCommands().getRoot().getChild(command) != null;
    }
    public static List<String> commands(Target target, ModerationRules.Rule rule, boolean warn, boolean mute,
                                         boolean ban, String muteTime, String banTime, String action) {
        if (target == null || !target.name().matches("[A-Za-z0-9_]{1,16}")) return List.of();
        String name = target.name();
        if (action.equals("teleport")) return List.of("tp " + name);
        if (action.equals("check")) return List.of("cheatcheck " + name);
        if (action.equals("pass")) return List.of("cheatpass " + name);
        if (action.equals("history")) return List.of("profile admin " + name);
        if (rule == null || !ModerationRules.ALL.contains(rule)) return List.of();
        List<String> result = new ArrayList<>();
        if (warn && rule.section() != 5) result.add("warn " + name + " " + rule.number());
        if (mute && rule.section() == 1 && dev.aeltorx.inflow.common.PunishmentDuration.valid(muteTime,false))
            result.add("mute " + name + " " + muteTime + " " + rule.number());
        if (ban && rule.section() != 1 && dev.aeltorx.inflow.common.PunishmentDuration.valid(banTime,true))
            result.add(banTime.equals("permanent") ? "ban " + name + " " + rule.number()
                : "tempban " + name + " " + banTime + " " + rule.number());
        return List.copyOf(result);
    }
    public static boolean submit(Target target, List<String> commands, LocalModerationAnimation.Style style) {
        if (!allowed() || !online(target)) { notice("Нет OP-прав или игрок уже вышел."); return false; }
        boolean punishment=commands.stream().anyMatch(c->c.startsWith("warn ")||c.startsWith("mute ")||c.startsWith("ban ")||c.startsWith("tempban "));
        if(punishment&&!ModerationWorkspace.ready()){notice("Проверка конфликта требует серверный модуль InFlow. Наказание не отправлено.");return false;}
        if (pending != null || commands.isEmpty() || System.nanoTime() - lastSubmission < 2_000_000_000L) return false;
        for (String command : commands) {
            if (command.length() > 256 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0
                || !commandAvailable(command.split(" ", 2)[0])) {
                notice("Сервер не предоставляет команду: " + command.split(" ", 2)[0]); return false;
            }
        }
        lastSubmission = System.nanoTime();
        var mc = Minecraft.getInstance();
        if(ModerationWorkspace.ready()&&commands.stream().anyMatch(c->c.startsWith("warn ")||c.startsWith("mute ")||c.startsWith("ban ")||c.startsWith("tempban "))){
            ModerationWorkspace.preparePunishment(target,commands,style);return true;
        }
        boolean hasBan=commands.stream().anyMatch(c -> c.startsWith("ban ") || c.startsWith("tempban "));
        if(hasBan&&style==LocalModerationAnimation.Style.DRONE) {
            if(!commandAvailable("wcpilot")||!(net.minecraft.client.Minecraft.getInstance().getConnection()!=null&&net.minecraft.client.Minecraft.getInstance().getConnection().hasChannel(DroneMovePayload.TYPE))){notice("Ручной дрон требует серверный модуль InFlow на сервере. Бан не отправлен.");return false;}
            var bc=commands.stream().filter(c->c.startsWith("ban ")||c.startsWith("tempban ")).findFirst().orElseThrow().split(" ");
            boolean permanent=bc[0].equals("ban");
            String duration=permanent?"permanent":bc[2],reason=bc[permanent?2:3];
            mc.getConnection().sendCommand("wcpilot start "+target.name()+" "+duration+" "+reason+(commands.stream().anyMatch(c->c.startsWith("warn "))?" warn":""));
            mc.gui.setScreen(new DronePilotScreen(target));return true;
        }
        if(hasBan && style!=LocalModerationAnimation.Style.NONE && commandAvailable("wcanim")) {
            var bc=commands.stream().filter(c->c.startsWith("ban ")||c.startsWith("tempban ")).findFirst().orElseThrow().split(" ");
            boolean permanent=bc[0].equals("ban");
            String duration=permanent?"permanent":bc[2],reason=bc[permanent?2:3];
            boolean withWarn=commands.stream().anyMatch(c->c.startsWith("warn "));
            mc.getConnection().sendCommand("wcanim ban "+target.name()+" "+style.name()+" "+duration+" "+reason+(withWarn?" warn":""));
            remoteConnection=mc.getConnection();remoteUntil=System.nanoTime()+(long)(dev.aeltorx.inflow.common.SceneTimeline.banAt(style.name())*1e9);
            notice("Общая сцена запрошена у сервера. F9 отменяет ожидание до момента бана.");return true;
        }
        boolean animated=hasBan && LocalModerationAnimation.start(target, style);
        if(animated && hasBan) {
            pending=new Pending(target,List.copyOf(commands),mc.getConnection(),System.nanoTime()+(long)(dev.aeltorx.inflow.common.SceneTimeline.banAt(style.name())*1e9));
            notice("Наказание подтверждено: команды будут отправлены после анимации. F9 → отменить ожидание.");
        } else {
            for (String command : commands) mc.getConnection().sendCommand(command);
            notice("Команды отправлены для " + target.name() + ". Результат проверяй в ответе сервера.");
        }
        return true;
    }
    public static void tick() {
        if(pending==null)return;
        var mc=Minecraft.getInstance();
        if(mc.getConnection()!=pending.connection || !allowed() || !online(pending.target)) {
            cancelPending(); notice("Отложенное наказание отменено: выход игрока, смена сервера или потеря прав."); return;
        }
        if(System.nanoTime()<pending.due)return;
        var current=pending;pending=null;
        for(String command:current.commands)mc.getConnection().sendCommand(command);
        notice("Команды отправлены для "+current.target.name()+". Результат проверяй в ответе сервера.");
    }
    public static boolean cancelPending() {
        if(remoteConnection!=null) {
            var mc=Minecraft.getInstance();boolean waiting=System.nanoTime()<remoteUntil&&remoteConnection==mc.getConnection();
            remoteConnection=null;if(waiting){mc.getConnection().sendCommand("wcanim cancel");return true;}
        }
        if(pending==null)return false;
        pending=null;LocalModerationAnimation.clear();return true;
    }
    static void remotelyStarted(Target target,LocalModerationAnimation.Style style){
        var mc=Minecraft.getInstance();if(style==LocalModerationAnimation.Style.DRONE){mc.gui.setScreen(new DronePilotScreen(target));return;}
        remoteConnection=mc.getConnection();remoteUntil=System.nanoTime()+(long)(dev.aeltorx.inflow.common.SceneTimeline.banAt(style.name())*1e9);
    }
    public static void notice(String text) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[InFlow] " + text));
    }
}
