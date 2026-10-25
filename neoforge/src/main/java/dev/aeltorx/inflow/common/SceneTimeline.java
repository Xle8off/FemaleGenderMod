package dev.aeltorx.inflow.common;

/** Target-relative choreography shared by both renderers, in metres and seconds. */
public final class SceneTimeline {
    public record Pose(double x,double y,double z,double yaw,double scale) { }
    private SceneTimeline() { }
    public static double banAt(String style) {
        if(CinematicScenes.STYLES.contains(style))return CinematicScenes.banAt(style);
        return switch(style) {case "VAN" -> 6.3;case "MINIONS" -> 3.2;case "WARDEN" -> 7.5;case "SPIDER" -> 7.2;case "UFO" -> 6.2;case "PORTAL" -> 5.8;case "ANVIL" -> 5.2;case "TANK" -> 3.4;case "NUKE" -> 3.6;default -> 2.8;};
    }
    public static double length(String style) { if(CinematicScenes.STYLES.contains(style))return banAt(style)+1.4;return switch(style){case "VAN"->10;case "WARDEN"->9;case "SPIDER"->8.5;case "UFO"->8;case "PORTAL"->7;case "ANVIL"->6.5;default->6;}; }
    public static boolean hasDouble(String style){return CinematicScenes.STYLES.contains(style)||style.equals("UFO")||style.equals("PORTAL")||style.equals("ANVIL");}
    public static String victimPose(String style,double t){
        if(style.equals("ANVIL")&&t>=2.4)return "SWIMMING";
        if(style.equals("PATRICK"))return t>=9?"CROUCHING":t>=5.2?"SWIMMING":"STANDING";
        if(style.equals("SHREK"))return t>=8.9?"CROUCHING":t>=5.2?"SWIMMING":"STANDING";
        return "STANDING";
    }
    /** Visual skin double only: the real player is never launched, crushed or teleported. */
    public static Pose victim(String style,double t){
        if(CinematicScenes.STYLES.contains(style))return CinematicScenes.victim(style,t);
        return switch(style){
            case "UFO"->new Pose(0,ease((t-2)/3.5)*4.1,0,t*.8,1-ease((t-5.4)/.6));
            case "PORTAL"->new Pose(0,.08*Math.sin(t*8),-2.6*ease((t-2)/3.3),ease((t-2)/3.3)*Math.PI*2,1-ease((t-4.6)/.9));
            case "ANVIL"->new Pose(0,t<2.4?0:.04,0,0,t<2.4?1:.45);
            default->new Pose(0,0,0,0,1);
        };
    }
    private static double ease(double t) { t=Math.clamp(t,0,1);return t*t*(3-2*t); }
    public static Pose pose(String style,int group,double t) {
        if(CinematicScenes.STYLES.contains(style))return CinematicScenes.pose(style,group,t);
        if(group>=200)group/=100;
        double x=0,y=0,z=0,yaw=0,scale=1;
        switch(style) {
            case "VAN" -> {
                x=-9+ease(t/1.8)*9;z=-2.1;yaw=Math.PI/2;
                if(t>6.3)x+=ease((t-6.3)/3.7)*12;
                double door=ease((t-1.8)/.6)*(1-ease((t-5.7)/.55));
                if(group==1)x-=door*1.55;
                if(group>=10) {
                    double side=group==10?-1:1;
                    double out=ease((t-2.4)/1.2),back=ease((t-4.2)/1.4);
                    x+=side*.58;z=-1.5+out*1.6-back*1.9;yaw=back*Math.PI;
                    y=.025*Math.sin(t*18)*(1-back);
                    scale=t<2.4||t>6.2?0:1;
                }
            }
            case "MINIONS" -> {
                z=-4+ease(t/1.7)*3.3;
                y=t<1.7?Math.abs(Math.sin(t*16))*.05:.12*ease((t-2.6)/.6)*(1-ease((t-3.2)/.6));
            }
            case "TANK" -> {
                z=-4+ease(t/1.5)*.3;
                if(group==2)y=1.4;
                if(group==3) {scale=t<2.2 || t>3.4?0:1; z=-3.7+ease((t-2.2)/1.2)*1.2;y=.2;}
            }
            case "DRONE" -> {z=-8+ease(t/2.8)*8;y=Math.max(.5,5*(1-ease(t/2.8)));scale=t>2.8?0:1;}
            case "SPIDER" -> {
                if(group==90){scale=ease((t-1.7)/1.5)*(1-ease((t-7.2)/.4));}
                else{y=4*(1-ease(t/1.6));z=-1.35;
                    if(t>3.3&&t<6.5){x=Math.sin((t-3.3)*5)*.22;y+=Math.abs(Math.sin((t-3.3)*7))*.09;yaw=Math.sin((t-3.3)*4)*.45;}
                    if(t>6.5){y+=ease((t-6.5)/.7)*5;scale=1-ease((t-7)/.2);}
                }
            }
            case "DYNAMITE" -> {y=Math.max(0,2*(1-ease(t/1.7)));scale=t>2.8?0:1;}
            case "WARDEN" -> {
                z=-3.4+ease(t/1.2)*.7;
                if(group==90){double rise=ease((t-3)/1.4),catchSoul=ease((t-4.4)/1.9);
                    z=-2.35*catchSoul;y=.25+rise*1.7;scale=ease((t-3)/.35)*(1-ease((t-6.3)/.8));}
            }
            case "NUKE" -> {if(group==4){scale=t<3.6?0:ease((t-3.6)/1.2)*2;y=(t-3.6)*.4;}
                else{y=Math.max(0,10*(1-ease(t/3.6)));scale=t>3.6?0:1;}}
            case "UFO" -> {
                if(group==90){scale=ease((t-1.6)/.4)*(1-ease((t-6)/.25));}
                else{x=-7*(1-ease(t/1.5));y=4.3+Math.sin(t*2)*.06;yaw=t*.45;
                    if(t>6.2){x+=ease((t-6.2)/1.8)*9;y+=ease((t-6.2)/1.8)*5;}}
            }
            case "PORTAL" -> {z=-2.6;scale=ease(t/1.3)*(1-ease((t-5.8)/.8));yaw=.08*Math.sin(t*2);}
            case "ANVIL" -> {
                if(group==90){scale=t<2.4||t>5.2?0:1;yaw=t*2;}
                else{y=t<2.4?6*(1-ease((t-1)/1.4)):.3*Math.abs(Math.sin((t-2.4)*7))*Math.max(0,1-(t-2.4)/1.2);
                    if(t>4.2){y+=ease((t-4.2)/1)*5;scale=1-ease((t-5)/.2);}}
            }
            default -> { }
        }
        return new Pose(x,y,z,yaw,scale);
    }
    public static boolean explosion(String style) {
        return style.equals("TANK")||style.equals("NUKE")||style.equals("DRONE")||style.equals("DYNAMITE");
    }
}
