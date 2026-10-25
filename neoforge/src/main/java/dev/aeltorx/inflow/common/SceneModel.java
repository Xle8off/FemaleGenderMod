package dev.aeltorx.inflow.common;
import java.util.*;
/** Renderer-neutral model geometry, shared by client and Leaf plugin. */
public final class SceneModel {
    public record Part(String material,double x,double y,double z,float w,float h,float d,int group) {}
    private static final ThreadLocal<List<Part>> output=new ThreadLocal<>();
    public static List<Part> build(String style) {
        if(CinematicScenes.STYLES.contains(style))return CinematicScenes.build(style);
        output.set(new ArrayList<>());
        try {
            switch(style) {
                case "VAN" -> { van(); minion(10); minion(11); }
                case "MINIONS" -> {
                    minion(10);
                    // Separate palm grip: the club is not embedded into the arm.
                    box("DARK_OAK_PLANKS",.335,.5,.19,.065f,.55f,.065f,1005);
                    box("BROWN_CONCRETE",.328,1.04,.182,.08f,.15f,.08f,1005);
                    box("DARK_OAK_PLANKS",.308,1.18,.162,.12f,.4f,.12f,1005);
                    box("DARK_OAK_PLANKS",.318,1.58,.172,.1f,.09f,.1f,1005);
                    box("DARK_OAK_PLANKS",.335,1.67,.19,.065f,.05f,.065f,1005);
                }
                case "DRONE" -> drone();
                case "SPIDER" -> spider();
                case "DYNAMITE" -> dynamite();
                case "WARDEN" -> {
                    // Soul only; the Warden itself remains the actual vanilla entity.
                    box("SEA_LANTERN",-.13,.6,-.13,.26f,.27f,.26f,90);
                    box("LIGHT_BLUE_STAINED_GLASS",-.17,.12,-.12,.34f,.48f,.24f,90);
                    box("CYAN_STAINED_GLASS",-.26,.3,-.07,.52f,.1f,.14f,90);
                    box("LIGHT_BLUE_STAINED_GLASS",-.09,-.16,-.09,.18f,.28f,.18f,90);
                }
                case "TANK" -> tank();
                case "NUKE" -> nuke();
                case "UFO" -> ufo();
                case "PORTAL" -> portal();
                case "ANVIL" -> anvil();
                default -> throw new IllegalArgumentException("Unknown style");
            }
            return List.copyOf(output.get());
        } finally { output.remove(); }
    }
    private static void box(String block,double x,double y,double z,float w,float h,float d,int group) {
        output.get().add(new Part(block,x,y,z,w,h,d,group));
    }
    private static void van() {
        // Hollow passenger compartment; the sliding door is a separately animated group.
        box("BLACK_CONCRETE",-1.1,.4,-1.9,2.2f,.18f,3.8f,0);
        box("BLACK_CONCRETE",-1.05,2,-1.85,2.1f,.14f,3.7f,0);
        box("BLACK_CONCRETE",-1.1,.58,-1.9,2.2f,1.45f,.12f,0);
        box("BLACK_CONCRETE",1.0,.58,-1.85,.1f,1.45f,3.4f,0);
        box("BLACK_CONCRETE",-1.1,.58,-1.85,.1f,1.45f,.55f,0);
        box("BLACK_CONCRETE",-1.1,.58,.35,.1f,1.45f,1.2f,0);
        box("BLACK_CONCRETE",-1.1,.58,-1.3,.1f,1.45f,1.65f,1);
        box("GRAY_CONCRETE",-1.125,1.18,-.2,.025f,.08f,.3f,1);
        box("GRAY_CONCRETE",-.85,.6,-1.5,.5f,.15f,1.8f,0);
        box("BLACK_CONCRETE",-1.05,.6,1.4,2.1f,.65f,.55f,0);
        box("GRAY_STAINED_GLASS",-.95,1.3,1.5,1.9f,.66f,.08f,0);
        for (int side : new int[]{-1, 1}) {
            box("GRAY_STAINED_GLASS", side == -1 ? -1.12 : 1.02, 1.32, .45, .1f, .62f, .85f, 0);
            for (double z : new double[]{-1.2, 1.1}) {
                box("BLACK_CONCRETE", side == -1 ? -1.25 : .95, .1, z, .3f, .65f, .65f, 0);
                box("GRAY_CONCRETE", side == -1 ? -1.28 : 1.24, .28, z + .18, .05f, .28f, .28f, 0);
            }
        }
        for (double x : new double[]{-.9, .65}) box("SEA_LANTERN", x, .8, 1.91, .25f, .22f, .07f, 0);
        box("BLACK_CONCRETE", -.7, .5, 1.92, 1.4f, .2f, .06f, 0);
        for(double x:new double[]{-.93,.7})box("RED_CONCRETE",x,.85,-1.93,.23f,.27f,.04f,0);
        box("LIGHT_GRAY_CONCRETE",-.28,.7,1.96,.56f,.14f,.02f,0);
    }
    private static void minion(int group) {
        // Layered capsule silhouette replaces the old rectangular yellow brick.
        for(int i=0;i<10;i++) {
            double y=.42+i*.09;
            double r=.3*Math.sqrt(Math.max(.03,1-Math.pow((y-.83)/.46,2)));
            for(int j=0;j<3;j++) {
                double depth=r*2/3,z=-r+j*depth;
                double half=r*Math.sqrt(1-Math.pow((z+depth/2)/r,2));
                box("YELLOW_CONCRETE",-half,y,z,(float)(half*2),.095f,(float)depth,group);
            }
        }
        box("BLUE_CONCRETE", -.27, .15, -.22, .54f, .43f, .44f, group);
        for(double x:new double[]{-.23,.13})box("BLUE_CONCRETE",x,.55,-.22,.1f,.22f,.04f,group);
        box("BLACK_CONCRETE", -.31, 1.0, -.28, .62f, .14f, .56f, group);
        // No oversized iron rectangle across the whole face.
        // One deliberately designed goggle/eye, not layered leftover pupils.
        for(double cx:new double[]{0}) {
            for(int i=0;i<12;i++) {
                double angle=i*Math.PI/6;
                box("LIGHT_GRAY_CONCRETE",cx+Math.cos(angle)*.155-.025,1.07+Math.sin(angle)*.155-.025,.28,.05f,.05f,.07f,group);
            }
            box("WHITE_CONCRETE",cx-.12,.96,.32,.24f,.24f,.035f,group);
            box("BROWN_CONCRETE",cx-.055,1.02,.36,.11f,.12f,.02f,group);
            box("BLACK_CONCRETE",cx-.028,1.04,.385,.056f,.075f,.01f,group);
            box("LIGHT_GRAY_CONCRETE",cx+.018,1.087,.396,.025f,.025f,.005f,group);
        }
        box("BLACK_CONCRETE",-.09,.8,.28,.18f,.025f,.025f,group);
        box("BLACK_CONCRETE",-.24,0,-.12,.18f,.23f,.34f,group*100+1);
        box("BLACK_CONCRETE",.08,0,-.12,.18f,.23f,.34f,group*100+2);
        box("YELLOW_CONCRETE",-.43,.52,-.1,.13f,.4f,.18f,group*100+3);
        box("YELLOW_CONCRETE",.3,.71,-.1,.13f,.21f,.18f,group*100+4);
        box("YELLOW_CONCRETE",.3,.5,-.1,.13f,.23f,.18f,group*100+6);
        box("BLACK_CONCRETE",.285,.45,.045,.16f,.14f,.18f,group*100+6);
    }
    private static void drone() {
        box("LIGHT_GRAY_CONCRETE", -.14, -.1, -.9, .28f, .22f, 1.8f, 0);
        box("LIGHT_GRAY_CONCRETE", -1.5, 0, -.6, 3f, .08f, .7f, 0);
        box("GRAY_CONCRETE", -.6, .02, -.8, 1.2f, .07f, .25f, 0);
        box("BLACK_CONCRETE", -.5, -.02, -1, 1f, .06f, .1f, 1);
        box("GRAY_CONCRETE", -.1, .05, -.85, .2f, .4f, .2f, 0);
    }
    private static void spider() {
        // Tapered mask and torso rather than one square head and slab body.
        for(int i=0;i<8;i++){
            double r=.235*Math.sqrt(Math.max(.18,1-Math.pow((i-3.5)/4.3,2)));
            box("RED_CONCRETE",-r,1.39+i*.052,-r,(float)(2*r),.055f,(float)(2*r),0);
        }
        for(int i=0;i<8;i++){float w=.36f+i*.025f;
            box("RED_CONCRETE",-w/2,.68+i*.085,-.15,w,.087f,.3f,0);
            box("BLACK_CONCRETE",-w/2,.7+i*.085,.154,w,.01f,.008f,0);
        }
        box("BLUE_CONCRETE",-.22,.57,-.14,.44f,.14f,.28f,0);
        for(int side:new int[]{-1,1}){
            int arm=side<0?101:102,leg=side<0?103:104;
            double ax=side<0?-.43:.28,lx=side<0?-.22:.06;
            box("RED_CONCRETE",ax,.91,-.1,.15f,.37f,.2f,arm);
            box("BLUE_CONCRETE",ax,.74,-.095,.15f,.18f,.19f,arm);
            box("RED_CONCRETE",ax,.58,-.105,.15f,.18f,.21f,arm);
            box("BLUE_CONCRETE",lx,.25,-.12,.16f,.37f,.24f,leg);
            box("RED_CONCRETE",lx,.02,-.13,.16f,.25f,.29f,leg);
            double ex=side<0?-.19:.035;
            box("BLACK_CONCRETE",ex-.018,1.58,.215,.175f,.14f,.018f,0);
            box("WHITE_CONCRETE",ex,1.605,.237,.135f,.092f,.009f,0);
            for(int i=0;i<4;i++)box("BLACK_CONCRETE",ax,.6+i*.17,.112,.15f,.012f,.008f,arm);
        }
        box("BLACK_CONCRETE",-.035,.99,.168,.07f,.14f,.02f,0);
        for(int i=0;i<4;i++)box("BLACK_CONCRETE",-.12,.98+i*.042,.17,.24f,.012f,.015f,0);
        for(double x:new double[]{-.13,0,.13})box("BLACK_CONCRETE",x,1.41,.22,.008f,.37f,.008f,0);
        // Hollow cocoon: no real cobweb blocks or world edits.
        for(int i=0;i<12;i++)for(int side=0;side<4;side++){
            double y=.12+i*.14;
            box("WHITE_CONCRETE",side==0?-.38:side==1?.35:-.38,y,side==2?-.38:side==3?.35:-.38,
                side<2?.03f:.76f,.027f,side<2?.76f:.03f,90);
        }
    }
    private static void dynamite() {
        box("TNT",-.5,0,-.5,1,1,1,0);
    }
    private static void warden() {
        box("SCULK", -.55, .65, -.35, 1.1f, 1.3f, .7f, 0);
        box("SCULK", -.45, 1.95, -.35, .9f, .65f, .7f, 0);
        for (double x : new double[]{-.9, .55}) {
            box("SCULK", x, .7, -.3, .35f, 1.2f, .6f, 0);
            box("CYAN_CONCRETE", x, 2.25, -.1, .35f, .55f, .2f, 0);
        }
        for (double x : new double[]{-.4, .1}) box("SCULK", x, 0, -.3, .3f, .7f, .6f, 0);
        box("CYAN_CONCRETE", -.26, 1.1, .36, .52f, .5f, .04f, 0);
    }
    private static void tank() {
        box("GREEN_CONCRETE",-1,.3,-1.2,2f,.55f,2.8f,0);
        for(double x:new double[]{-1.15,.85}) {
            box("BLACK_CONCRETE",x,.05,-1.35,.3f,.6f,3f,0);
            for(int i=0;i<5;i++)box("GRAY_CONCRETE",x-.02,.18,-1.1+i*.55,.34f,.32f,.35f,0);
        }
        box("GREEN_CONCRETE",-.6,.85,-.45,1.2f,.55f,1.4f,0);
        box("GRAY_CONCRETE",-.14,1.05,.9,.28f,.25f,1.5f,0);
        box("BLACK_CONCRETE",-.15,1.03,2.35,.3f,.3f,.12f,0);
        minion(2);
        box("GRAY_CONCRETE",-.08,1.08,2.5,.16f,.16f,.25f,3);
    }
    private static void ufo(){
        // Rounded saucer made of thin circular slices, with a glass cockpit and rim lights.
        for(int layer=0;layer<8;layer++){
            double y=layer*.085,r=1.85*Math.sqrt(Math.max(.08,1-Math.pow((layer-2.8)/4.5,2)));
            for(int strip=0;strip<7;strip++){
                double d=2*r/7,z=-r+strip*d,half=r*Math.sqrt(Math.max(0,1-Math.pow((z+d/2)/r,2)));
                box(layer<2?"GRAY_CONCRETE":"LIGHT_GRAY_CONCRETE",-half,y,z,(float)(half*2),.09f,(float)d,0);
            }
        }
        for(int i=0;i<5;i++){double r=.68*Math.sqrt(Math.max(.05,1-Math.pow(i/5.0,2)));
            box("LIGHT_BLUE_STAINED_GLASS",-r,.62+i*.1,-r,(float)(2*r),.11f,(float)(2*r),0);}
        for(int i=0;i<20;i++){double a=i*Math.PI/10;
            box(i%2==0?"SEA_LANTERN":"CYAN_CONCRETE",Math.cos(a)*1.72-.065,.16,Math.sin(a)*1.72-.065,.13f,.12f,.13f,0);
        }
        box("SEA_LANTERN",-.3,-.08,-.3,.6f,.12f,.6f,0);
        for(int i=0;i<16;i++){double a=i*Math.PI/8;
            box("LIGHT_BLUE_STAINED_GLASS",Math.cos(a)*.68-.035,.03,Math.sin(a)*.68-.035,.07f,4.3f,.07f,90);
        }
    }
    private static void portal(){
        for(int i=0;i<40;i++){
            double a=i*Math.PI/20;
            box("PURPLE_CONCRETE",Math.cos(a)*1.05-.10,1.3+Math.sin(a)*1.35-.10,-.12,.2f,.2f,.24f,0);
            box("AMETHYST_BLOCK",Math.cos(a)*1.17-.055,1.3+Math.sin(a)*1.49-.055,-.045,.11f,.11f,.09f,0);
        }
        for(int i=0;i<12;i++){double y=.05+i*.22,w=2*Math.sqrt(Math.max(0,1-Math.pow((y-1.3)/1.35,2)));
            box("PURPLE_STAINED_GLASS",-w/2,y,-.025,(float)w,.225f,.05f,0);}
    }
    private static void anvil(){
        box("ANVIL",-.75,0,-.75,1.5f,1.5f,1.5f,0);
        for(int i=0;i<5;i++){double a=i*Math.PI*2/5;
            double x=Math.cos(a)*.7,z=Math.sin(a)*.7;
            box("YELLOW_CONCRETE",x-.09,.38,z-.035,.18f,.04f,.07f,90);
            box("YELLOW_CONCRETE",x-.025,.32,z-.035,.05f,.16f,.07f,90);
        }
    }
    private static void nuke() {
        // Cinematic gravity-bomb silhouette, with rounded nose and tail fins.
        for(int i=0;i<20;i++) {
            double y=i*.1;
            double r=y<.55?.34*Math.sqrt(Math.max(.018,1-Math.pow((y-.55)/.55,2))):y<1.5?.34:.34-(y-1.5)*.32;
            String material=i==9||i==10?"YELLOW_CONCRETE":"LIGHT_GRAY_CONCRETE";
            for(int j=0;j<5;j++) {
                double d=2*r/5,z=-r+j*d,half=r*Math.sqrt(1-Math.pow((z+d/2)/r,2));
                box(material,-half,y,z,(float)(half*2),.105f,(float)d,0);
            }
        }
        box("GRAY_CONCRETE",-.16,1.98,-.16,.32f,.38f,.32f,0);
        for(double side:new double[]{-1,1}) {
            box("GRAY_CONCRETE",side<0?-.5:.14,1.85,-.035,.36f,.55f,.07f,0);
            box("GRAY_CONCRETE",-.035,1.85,side<0?-.5:.14,.07f,.55f,.36f,0);
        }
        box("BLACK_CONCRETE",-.16,2.37,-.16,.32f,.06f,.32f,0);
        // Mushroom cloud is now smoke particles, not a stack of translucent cubes.
    }

}
