package dev.aeltorx.inflow.common;

import java.util.*;
import static dev.aeltorx.inflow.common.SceneTimeline.Pose;

/** Authored mini-stories: persistent sets, independent props and articulated character rigs. */
public final class CinematicScenes {
    public static final List<String> STYLES=List.of("PATRICK","SHREK","LUIGI","POKEBALL","REAPER","COURIER","JUDGE","HAND","MAGICIAN","FISHER");
    private final List<SceneModel.Part> parts=new ArrayList<>();
    private void b(String m,double x,double y,double z,double w,double h,double d,int g){parts.add(new SceneModel.Part(m,x,y,z,(float)w,(float)h,(float)d,g));}
    /** Rounded elliptical cross-sections, not a single rectangular body. */
    private void egg(String m,double cx,double cy,double cz,double rx,double ry,double rz,int slices,int g){
        for(int i=0;i<slices;i++){double yy=-1+(i+.5)*2/slices,r=Math.sqrt(1-yy*yy);
            for(int j=0;j<3;j++){double zz=-1+(j+.5)*2/3,half=Math.sqrt(1-zz*zz)*rx*r;
                b(m,cx-half,cy+yy*ry-ry/slices,cz+zz*rz*r-rz*r/3,half*2,ry*2/slices+.004,rz*r*2/3+.004,g);}}
    }
    private void eyes(double y,double z,double gap,int g){for(double x:new double[]{-gap,gap}){
        egg("WHITE_CONCRETE",x,y,z,.10,.145,.065,3,g);b("BLACK_CONCRETE",x-.026,y-.03,z+.066,.052,.072,.016,g);}}
    private void face(double y,double z,int g){eyes(y,z,.115,g);b("BLACK_CONCRETE",-.105,y-.26,z-.01,.21,.023,.035,g);}
    private void door(String material,double x,double z,int g){b(material,x,0,z,.85,2,.09,g);b("GOLD_BLOCK",x+.68,.9,z+.09,.06,.08,.035,g);}
    private void fittedDoor(String material,double x,double y,double z,double width,double height,int g){
        b(material,x,y,z,width,height,.09,g);
        b("GOLD_BLOCK",x+width-.16,y+height*.46,z+.09,.07,.10,.035,g);
    }
    private void humanoid(String shirt,String trousers,String skin,boolean tall){
        double k=tall?1.18:1;
        egg(shirt,0,.99*k,0,.32,.37*k,.20,6,20);
        egg(skin,0,1.58*k,.015,.235,.29*k,.23,6,20);face(1.67*k,.25,20);
        for(int side:new int[]{-1,1}){
            int arm=side<0?2001:2002,leg=side<0?2003:2004;double x=side<0?-.46:.31;
            b(shirt,x,.88*k,-.11,.15,.39*k,.22,arm);b(skin,x,.63*k,-.105,.15,.25*k,.21,arm);
            egg(skin,x+.075,.61*k,.02,.095,.10,.10,3,arm);
            b(trousers,side<0?-.26:.07,.18,-.13,.19,.48*k,.26,leg);
            b("BLACK_CONCRETE",side<0?-.27:.06,.02,-.1,.21,.17,.36,leg);
        }
    }
    private void room(String wall){
        b("SMOOTH_SANDSTONE",-2.2,-.12,-6.8,4.4,.12,3.8,0);
        b(wall,-2.2,0,-6.8,4.4,1.55,.12,0);
        b(wall,-2.2,0,-6.8,.12,1.55,3.8,0);b(wall,2.08,0,-6.8,.12,1.55,3.8,0);
    }
    private void patrick(){
        // Round sandy footprint. No square walls or box-shaped room.
        for(int i=0;i<13;i++){double z=-2.05+(i+.5)*4.1/13,half=2.25*Math.sqrt(Math.max(0,1-z*z/(2.05*2.05)));
            b("SMOOTH_SANDSTONE",-half,-.10,-4.9+z-2.05/13,half*2,.10,4.1/13,0);}
        // Open-front furnished cutaway underneath the lifting rock, not a flat backdrop.
        b("ORANGE_CONCRETE",-.85,0,-5.95,1.7,.35,.8,0);b("ORANGE_CONCRETE",-.85,.35,-6.15,1.7,.5,.20,0);
        for(double x:new double[]{-.95,.75})b("BROWN_CONCRETE",x,.18,-6.02,.2,.5,.85,0);
        b("SANDSTONE",.55,.34,-4.5,.85,.12,.55,0);b("SANDSTONE",.91,0,-4.33,.14,.34,.16,0);
        b("BROWN_CONCRETE",1.12,.5,-6.42,.78,.58,.22,0);b("BLACK_CONCRETE",1.18,.56,-6.17,.66,.44,.035,0);
        b("LIGHT_BLUE_CONCRETE",1.23,.61,-6.13,.53,.31,.018,0);
        b("BLACK_CONCRETE",-1.75,0,-5.6,.32,.08,.32,0);b("GRAY_CONCRETE",-1.61,.08,-5.46,.06,1.08,.06,0);
        egg("YELLOW_CONCRETE",-1.58,1.32,-5.43,.25,.20,.25,3,0);
        // Low hemispherical rock, 65 contour pieces, hinged at its rear edge.
        for(int i=0;i<13;i++){double y=(i+.5)*1.65/13,r=Math.sqrt(Math.max(0,1-y*y/(1.65*1.65)));
            for(int j=0;j<5;j++){double z=(-1+(j+.5)*2/5)*2.1*r,half=2.35*r*Math.sqrt(Math.max(0,1-Math.pow(z/(2.1*r),2)));
                b("BROWN_TERRACOTTA",-half,y-1.65/26,-4.9+z-2.1*r/5,half*2,1.65/13+.008,4.2*r/5+.008,1);}}
        // Five-point pink silhouette, lime shorts, purple patches and a friendly face.
        egg("PINK_CONCRETE",0,.92,0,.44,.53,.31,7,20);
        for(int i=0;i<8;i++){double w=.32*(1-i/8.7);b("PINK_CONCRETE",-w,1.35+i*.085,-w*.7,2*w,.09,1.4*w,20);}
        b("LIME_CONCRETE",-.41,.38,-.31,.82,.31,.62,20);
        for(double x:new double[]{-.28,.17})b("PURPLE_CONCRETE",x,.48,.322,.13,.11,.013,20);
        face(1.64,.23,20);b("PINK_CONCRETE",-.024,.93,.317,.048,.075,.022,20);
        for(int side:new int[]{-1,1}){
            int arm=side<0?2001:2002,leg=side<0?2003:2004;
            for(int i=0;i<6;i++){double width=.18*(1-i/7.0);b("PINK_CONCRETE",side<0?-.38-(i+1)*.10:.38+i*.10,1.05-width,-width,.105,width*2,width*2,arm);}
            egg("PINK_CONCRETE",side*.22,.22,.02,.16,.24,.16,4,leg);
        }
    }
    private void shrek(){
        b("MOSS_BLOCK",-1.8,-.1,-6,3.6,.1,3.2,0);
        b("OAK_PLANKS",-1,0,-5.8,2,2.7,.12,0);
        for(double x:new double[]{-1, .88})b("OAK_PLANKS",x,0,-5.8,.12,2.7,1.7,0);
        b("DARK_OAK_PLANKS",-1.08,2.7,-5.88,2.16,.15,1.85,0);
        fittedDoor("SPRUCE_PLANKS",-.875,.01,-4.115,1.75,2.68,1);
        b("WHITE_CONCRETE",.12,.3,-5.48,.66,.54,.74,0);b("WHITE_CONCRETE",.12,.84,-5.6,.66,.68,.20,0);
        humanoid("WHITE_CONCRETE","BROWN_CONCRETE","LIME_CONCRETE",true);
        b("BROWN_CONCRETE",-.30,.86,-.218,.16,.65,.06,20);b("BROWN_CONCRETE",.14,.86,-.218,.16,.65,.06,20);
        b("BROWN_CONCRETE",-.31,.9,.203,.16,.52,.045,20);b("BROWN_CONCRETE",.15,.9,.203,.16,.52,.045,20);
        b("LIME_CONCRETE",-.095,1.85,.27,.19,.10,.11,20);
        for(double x:new double[]{-.32,.24}){b("LIME_CONCRETE",x,1.89,-.02,.09,.08,.20,20);b("GREEN_CONCRETE",x-.015,1.87,.17,.12,.12,.035,20);}
        b("BLACK_CONCRETE",-.2,2.37,-3.88,.4,.16,.012,0);
    }
    private void luigi(){
        humanoid("GREEN_CONCRETE","BLUE_CONCRETE","WHITE_TERRACOTTA",true);
        b("GREEN_CONCRETE",-.27,2,-.22,.54,.12,.48,20);b("GREEN_CONCRETE",-.24,2.12,-.21,.48,.11,.36,20);
        b("WHITE_CONCRETE",-.08,2.025,.27,.16,.11,.016,20);b("GREEN_CONCRETE",-.035,2.035,.288,.025,.07,.006,20);b("GREEN_CONCRETE",-.035,2.035,.288,.07,.018,.006,20);
        b("BROWN_CONCRETE",-.18,1.69,.29,.36,.055,.035,20);egg("WHITE_TERRACOTTA",0,1.8,.31,.09,.08,.10,3,20);
        b("BLUE_CONCRETE",-.2,.72,.21,.4,.44,.025,20);for(double x:new double[]{-.19,.12})b("BLUE_CONCRETE",x,1.06,.21,.07,.24,.025,20);
        for(double x:new double[]{-.15,.11})b("GOLD_BLOCK",x,1.08,.244,.04,.05,.012,20);
        b("RED_CONCRETE",-.25,.74,-.46,.5,.65,.25,20);b("GRAY_CONCRETE",.39,.73,.05,.15,.18,.7,2002);
        b("BLACK_CONCRETE",.35,.7,.7,.23,.24,.12,2002);
        egg("LIGHT_BLUE_STAINED_GLASS",.44,1.15,-.27,.16,.22,.16,4,20);
        for(int i=0;i<12;i++)b("GRAY_CONCRETE",.20+i*.018,.89+Math.sin(i*.23)*.08,-.37+i*.05,.10,.08,.10,20);
    }
    private void pokeball(){
        for(int i=0;i<12;i++){double y=-1+(i+.5)*2/12,r=.44*Math.sqrt(1-y*y);
            for(int j=0;j<5;j++){double z=-1+(j+.5)*2/5,w=r*Math.sqrt(1-z*z);
                b(i==5||i==6?"BLACK_CONCRETE":i>6?"RED_CONCRETE":"WHITE_CONCRETE",-w,.45+y*.44-.44/12,z*r-r/5,2*w,.078,2*r/5+.004,20);}}
        b("BLACK_CONCRETE",-.10,.35,.424,.2,.2,.04,20);egg("WHITE_CONCRETE",0,.45,.475,.068,.068,.025,3,20);
        int trainerStart=parts.size();humanoid("RED_CONCRETE","BLUE_CONCRETE","WHITE_TERRACOTTA",false);
        // Trainer moved to its own root; the ball remains root 20.
        for(int i=trainerStart;i<parts.size();i++){var p=parts.get(i);parts.set(i,new SceneModel.Part(p.material(),p.x(),p.y(),p.z(),p.w(),p.h(),p.d(),p.group()>=2000?3000+p.group()%100:30));}
    }
    private void reaper(){
        for(int i=0;i<9;i++){double w=.42-i*.015;b("BLACK_CONCRETE",-w,.12+i*.15,-w*.6,w*2,.155,w*1.2,20);}
        egg("BLACK_CONCRETE",0,1.75,0,.32,.37,.27,7,20);b("BLACK_CONCRETE",-.25,1.51,.25,.50,.48,.04,20);
        b("WHITE_CONCRETE",-.18,1.59,.30,.36,.29,.025,20);
        for(double x:new double[]{-.12,.05})b("BLACK_CONCRETE",x,1.74,.329,.075,.075,.012,20);
        for(int i=0;i<4;i++)b("BLACK_CONCRETE",-.115+i*.067,1.605,.33,.027,.045,.01,20);
        for(int side:new int[]{-1,1})b("BLACK_CONCRETE",side<0?-.52:.34,.7,-.10,.18,.7,.20,side<0?2001:2002);
        for(int side:new int[]{-1,1}){
            int g=side<0?2001:2002;double x=side<0?-.50:.36;
            b("WHITE_CONCRETE",x,.64,.12,.14,.10,.06,g);
            for(int i=0;i<4;i++){b("WHITE_CONCRETE",x+i*.037,.55,.14,.022,.10,.025,g);b("WHITE_CONCRETE",x+i*.037,.52,.16,.022,.035,.055,g);}
        }
        for(int i=0;i<6;i++)b("GRAY_CONCRETE",-.33+i*.13,.2,.255,.012,.45,.009,20);
        b("DARK_OAK_PLANKS",.49,.1,.16,.055,2.32,.055,2002);
        for(int i=0;i<9;i++)b("IRON_BLOCK",.49-i*.09,2.37-Math.pow(i/8.0,2)*.2,.12,.095,.085,.07,2002);
        b("SMOOTH_SANDSTONE",-.45,.8,.18,.35,.43,.035,2001);
        for(int i=0;i<4;i++)b("GRAY_CONCRETE",-.41,.86+i*.075,.224,.23,.008,.008,2001);
        door("BLACK_CONCRETE",-.45,-3.7,1);b("PURPLE_STAINED_GLASS",-.37,.12,-3.58,.7,1.73,.015,1);
    }
    private void courier(){
        humanoid("ORANGE_CONCRETE","BLUE_CONCRETE","WHITE_TERRACOTTA",false);
        b("ORANGE_CONCRETE",-.26,1.87,-.22,.52,.1,.48,20);b("WHITE_CONCRETE",-.07,1.88,.275,.14,.06,.01,20);
        b("GRAY_CONCRETE",-1.25,.12,-.1,.62,.1,1.5,30);b("GRAY_CONCRETE",-1.25,.12,-.1,.09,1,.08,30);
        for(double z:new double[]{0,1.05})egg("BLACK_CONCRETE",-1.05,.15,z,.14,.15,.14,3,30);
        // Five separate faces, then folding lid and tape: the player is actually inside a hollow box.
        b("BROWN_CONCRETE",-.55,0,-.5,1.1,.08,1,1);
        b("BROWN_CONCRETE",-.55,.08,-.5,.06,1.85,1,2);b("BROWN_CONCRETE",.49,.08,-.5,.06,1.85,1,3);
        b("BROWN_CONCRETE",-.55,.08,-.5,1.1,1.85,.06,4);b("BROWN_CONCRETE",-.55,.08,.44,1.1,1.85,.06,5);
        b("BROWN_CONCRETE",-.55,1.94,-.5,1.1,.07,1,6);b("YELLOW_CONCRETE",-.055,2.014,-.5,.11,.012,1,6);
        b("WHITE_CONCRETE",-.25,.95,.51,.5,.34,.01,5);for(int i=0;i<7;i++)b("BLACK_CONCRETE",-.20+i*.05,1.02,.522,.017,.16,.005,5);
        b("YELLOW_CONCRETE",.32,.81,.03,.035,.06,.55,2002);
    }
    private void judge(){
        humanoid("BLACK_CONCRETE","BLACK_CONCRETE","WHITE_TERRACOTTA",false);
        egg("WHITE_CONCRETE",0,1.85,-.02,.27,.12,.24,4,20);
        for(double side:new double[]{-1,1})for(int i=0;i<3;i++)egg("WHITE_CONCRETE",side*.245,1.72-i*.105,0,.075,.07,.11,2,20);
        b("WHITE_CONCRETE",-.12,1.17,.214,.24,.22,.02,20);
        b("DARK_OAK_PLANKS",-1.3,0,-2.8,2.6,1.2,.22,0);b("DARK_OAK_PLANKS",-1.4,1.2,-2.80,2.8,.14,.85,0);
        for(int i=0;i<7;i++)b("OAK_PLANKS",-1.12+i*.35,.17,-2.565,.08,.82,.028,0);
        b("DARK_OAK_PLANKS",.34,.74,.12,.055,.43,.055,2002);b("BROWN_CONCRETE",.20,1.13,.07,.34,.17,.16,2002);
        b("SMOOTH_SANDSTONE",-.48,.83,.2,.4,.54,.03,2001);
        for(int i=0;i<5;i++)b("GRAY_CONCRETE",-.43,.89+i*.075,.235,.29,.01,.005,2001);
        b("BLACK_CONCRETE",-.65,-.02,-.6,1.3,.03,1.2,1);b("OAK_PLANKS",-.65,0,-.6,1.3,.07,1.2,2);
    }
    private void hand(){
        egg("WHITE_TERRACOTTA",0,1.03,0,.60,.65,.22,12,20);
        egg("WHITE_TERRACOTTA",0,.28,0,.32,.44,.18,5,20);
        for(int i=0;i<4;i++){int g=2001+i;double x=-.39+i*.25,len=i==0?.84:i==1?1.02:i==2?.95:.72;
            // Roots overlap the upper palm; no disconnected fingertip slabs.
            egg("WHITE_TERRACOTTA",x,1.39+len/2,0,.108,len/2,.145,7,g);
            b("WHITE_CONCRETE",x-.063,1.39+len-.17,-.145,.126,.11,.008,g);
            for(int k=0;k<2;k++)b("TERRACOTTA",x-.054,1.54+k*.24,.146,.108,.008,.005,g);
        }
        egg("WHITE_TERRACOTTA",-.60,1.02,0,.20,.38,.19,7,2005);
        b("WHITE_CONCRETE",-.66,1.23,-.188,.12,.12,.008,2005);
        for(int i=0;i<2;i++)b("TERRACOTTA",-.22,.89+i*.18,.218,.44,.006,.003,20);
    }
    private void magician(){
        humanoid("BLACK_CONCRETE","BLACK_CONCRETE","WHITE_TERRACOTTA",false);
        b("BLACK_CONCRETE",-.32,1.91,-.3,.64,.055,.6,20);b("BLACK_CONCRETE",-.22,1.96,-.20,.44,.38,.4,20);
        b("RED_CONCRETE",-.224,1.98,.20,.448,.075,.012,20);
        b("WHITE_CONCRETE",-.13,1.11,.214,.26,.20,.02,20);
        for(double x:new double[]{-.13,.025})b("RED_CONCRETE",x,1.24,.24,.105,.055,.02,20);
        b("BLACK_CONCRETE",.34,.78,.08,.035,.035,.6,2002);b("WHITE_CONCRETE",.34,.78,.66,.035,.035,.1,2002);
        b("PURPLE_CONCRETE",-.70,0,-2.6,1.4,.10,.85,0);b("PURPLE_CONCRETE",-.70,.1,-2.6,.10,2.3,.85,0);
        b("PURPLE_CONCRETE",.60,.1,-2.6,.10,2.3,.85,0);b("PURPLE_CONCRETE",-.7,2.4,-2.6,1.4,.1,.85,0);
        b("PURPLE_CONCRETE",-.7,.1,-2.6,1.4,2.3,.06,0);
        fittedDoor("PURPLE_CONCRETE",-.595,.105,-1.77,1.19,2.29,1);
        // Reveal prop: a recognizable chicken, rather than a generic replacement cube.
        egg("WHITE_CONCRETE",0,.35,-2.1,.23,.26,.21,4,40);b("WHITE_CONCRETE",-.12,.5,-1.98,.24,.27,.22,40);
        b("YELLOW_CONCRETE",-.09,.54,-1.73,.18,.075,.17,40);b("RED_CONCRETE",-.04,.47,-1.74,.08,.075,.02,40);
        for(double x:new double[]{-.075,.075})b("YELLOW_CONCRETE",x,.02,-2.04,.035,.14,.035,40);
        for(double x:new double[]{-.115,.08})b("BLACK_CONCRETE",x,.66,-1.75,.035,.035,.018,40);
    }
    private void fisher(){
        humanoid("CYAN_CONCRETE","BROWN_CONCRETE","WHITE_TERRACOTTA",false);
        b("BROWN_CONCRETE",-.30,1.84,-.28,.6,.09,.56,20);b("BROWN_CONCRETE",-.22,1.93,-.2,.44,.18,.4,20);
        b("DARK_OAK_PLANKS",.36,.77,.08,.035,1.25,.035,2002);
        // Separate reel, segmented fishing line and a hook which tracks the visual victim.
        b("IRON_BLOCK",.37,.88,.07,.08,.08,.07,2002);
        for(int i=0;i<12;i++)b("WHITE_CONCRETE",0,.12+i*.095,0,.012,.10,.012,35);
        b("IRON_BLOCK",-.055,-.01,-.025,.11,.025,.05,35);b("IRON_BLOCK",-.055,.01,-.025,.025,.10,.05,35);
        for(int i=0;i<20;i++){double a=i*Math.PI/10;
            b("LIGHT_BLUE_STAINED_GLASS",Math.cos(a)*.65-.04,.03,Math.sin(a)*.65-.04,.08,1.65,.08,0);}
        b("LIGHT_GRAY_CONCRETE",-.71,1.7,-.71,1.42,.16,1.42,1);
        b("WHITE_CONCRETE",-.38,.75,.67,.76,.32,.012,0);
        // Raised pixel lettering on the jar's label: УЛОВ.
        for(int i=0;i<12;i++)b("BLACK_CONCRETE",-.31+i*.054,.86,.69,.025,.12,.007,0);
    }
    public static List<SceneModel.Part> build(String style){var c=new CinematicScenes();switch(style){
        case "PATRICK"->c.patrick();case "SHREK"->c.shrek();case "LUIGI"->c.luigi();case "POKEBALL"->c.pokeball();case "REAPER"->c.reaper();
        case "COURIER"->c.courier();case "JUDGE"->c.judge();case "HAND"->c.hand();case "MAGICIAN"->c.magician();case "FISHER"->c.fisher();default->throw new IllegalArgumentException(style);}
        return List.copyOf(c.parts);}
    private static double s(double x){x=Math.clamp(x,0,1);return Math.clamp(x*x*x*(x*(x*6-15)+10),0,1);}
    public static double banAt(String style){return switch(style){case "PATRICK"->11.7;case "SHREK"->10.6;case "LUIGI"->7.6;case "POKEBALL"->7.8;case "REAPER"->8.8;case "COURIER"->9.5;case "JUDGE"->7.5;case "HAND"->7.7;case "MAGICIAN"->9.2;case "FISHER"->9;default->throw new IllegalArgumentException(style);};}
    public static Pose pose(String style,int group,double t){
        int root=group>=200?group/100:group;double x=0,y=0,z=0,yaw=0,scale=1;
        switch(style){
            case "PATRICK"->{if(root==1){y=0;}
                if(root==20){z=t<2?-5.6:t<4.8?-5.6+4.6*s((t-2)/2.8):t<6?-1:t<9?-1-4.7*s((t-6)/3):-5.7;
                    yaw=Math.PI*s((t-5.5)/.5)*(1-s((t-8.7)/.3));y=t>=2&&t<9?.035*Math.abs(Math.sin(t*14)):0;scale=t<.8||t>11.3?0:1;}}
            case "SHREK"->{if(root==1){x=-1.90*s(t/.8)*(1-s((t-9.6)/.65));}
                if(root==20){z=t<1.4?-5:t<4.4?-5+4*s((t-1.4)/3):t<5.6?-1:t<8.9?-1-4*s((t-5.6)/3.3):-5;
                    x=-.32*s((t-7)/1.9);yaw=Math.PI*s((t-5.1)/.5);y=.025*Math.abs(Math.sin(t*12));scale=t>10.3?0:1;}}
            case "LUIGI"->{if(root==20){x=-2.2*(1-s(t/1.2));z=-1.6;y=t>=1.2&&t<2.6?.06*Math.sin(t*15):0;}}
            case "POKEBALL"->{if(root==30){x=-2.4;z=-1.6;}
                else{double p=s((t-1)/1.6);x=-2.4*(1-p);z=-1.6*(1-p);y=t<2.6?.6+Math.sin(p*Math.PI)*1.8:.05;
                    if(t>=4.8&&t<7.1){double q=(t-4.8)% .8;x=Math.sin(q/.8*Math.PI*2)*.08;y=.05+Math.abs(Math.sin(q/.8*Math.PI*2))*.035;}}}
            case "REAPER"->{if(root==1){scale=s((t-4.4)/.8)*(1-s((t-8.1)/.7));}
                if(root==20){z=-4+3*s(t/2);if(t>4.5)z-=2.5*s((t-4.5)/3);yaw=Math.PI*s((t-4.2)/.3);scale=1-s((t-7.5)/.7);}}
            case "COURIER"->{if(root==20||root==30){x=-4+(root==20?2.7:4)*s(t/2);z=-.5;if(t>7.5)x+=8*s((t-7.5)/2);}
                else{double close=s((t-3.2)/2);scale=root==6?s((t-5.3)/.55):s((t-2.5)/.65);
                    if(root==2)x=-1.8*(1-close);if(root==3)x=1.8*(1-close);if(root==4)z=-1.8*(1-close);if(root==5)z=1.8*(1-close);
                    if(t>7.5)x+=8*s((t-7.5)/2);}}
            case "JUDGE"->{if(root==20)z=-3.2;if(root==1||root==2){scale=root==1?s((t-4.3)/.4):1; if(root==2){x=1.4*s((t-4.3)/.7);scale=1-s((t-6.5)/.7);}}}
            case "HAND"->{y=4.5*(1-s(t/1.5));z=-.6;if(t>5.3){x+=8*s((t-5.3)/2.1);y+=3*s((t-5.3)/2.1);}}
            case "MAGICIAN"->{if(root==20){x=-1.7;z=-1.6;y=t>8.2?-.12*s((t-8.2)/.4):0;scale=1-s((t-9)/.4);}
                if(root==1){x=1.35*(1-s((t-2.5)/.65))+1.35*s((t-6.4)/.65);}
                if(root==40){y=.105;scale=s((t-6.4)/.4);}}
            case "FISHER"->{if(root==20){x=-2.3;z=-1.4;}
                if(root==35){var p=victim(style,t);x=p.x();y=p.y()+1.5*p.scale();z=p.z();scale=t<2.2||t>7?0:1;}
                if(root==1)y=2*(1-s((t-6.7)/.8));}
        }
        return new Pose(x,y,z,yaw,scale);
    }
    public static Pose victim(String style,double t){
        double x=0,y=0,z=0,yaw=0,scale=1;
        switch(style){
            case "PATRICK"->{double lift=s((t-4.8)/.7),carry=s((t-6)/3),sit=s((t-9)/.4);x=.23*lift;y=.85*lift*(1-sit)+.36*sit;z=-.7*lift-4.95*carry;yaw=Math.PI*s((t-5.5)/.5)*(1-s((t-8.7)/.3));scale=1-s((t-11.2)/.3);}
            case "SHREK"->{double lift=s((t-4.4)/.8),seat=s((t-8.7)/.3),lower=s((t-8.9)/.3);var actor=pose(style,20,t);
                x=lift*(actor.x()+.35*Math.cos(actor.yaw())+.2*Math.sin(actor.yaw()))*(1-seat)+.44*seat;
                z=lift*(actor.z()+.2*Math.cos(actor.yaw())-.35*Math.sin(actor.yaw()))*(1-seat)-5.1*seat;
                y=1.18*lift*(1-lower)+.84*lower;yaw=actor.yaw()*(1-seat);scale=1-s((t-9.7)/.3);}
            case "LUIGI"->{double p=s((t-2.8)/3);x=.44*p;y=.95*p;z=-1.9*p;scale=1-.94*p;if(t>6.5)scale*=1-s((t-6.5)/.4);}
            case "POKEBALL"->{scale=1-s((t-2.7)/1.4);y=.25*s((t-2.7)/1.4);}
            case "REAPER"->{x=.5*s((t-3)/1);z=-3.7*s((t-4.5)/3);yaw=Math.PI*s((t-4.5)/.8);scale=1-s((t-7.3)/.5);}
            case "COURIER"->{scale=.92;if(t>7.5)x=8*s((t-7.5)/2);}
            case "JUDGE"->{y=-3.5*s((t-4.4)/1.5);scale=1-s((t-5.5)/.5);}
            case "HAND"->{double pinch=s((t-3.5)/.8);y=.8*pinch;
                if(t>2.65&&t<3.3){double flick=Math.sin((t-2.65)/.65*Math.PI);x=.38*flick;y=.2*flick;}
                if(t>5.3){x=8*s((t-5.3)/2.1);y+=3*s((t-5.3)/2.1);}scale=1-s((t-7.3)/.3);}
            case "MAGICIAN"->{double enter=s((t-1.5)/1);z=-2.1*enter;y=.105*enter;scale=1-s((t-3.4)/.8);}
            case "FISHER"->{double p=s((t-2.8)/2.6);y=Math.sin(p*Math.PI)*3.1;scale=1-.65*p;yaw=p*Math.PI*4;}
        }
        return new Pose(x,y,z,yaw,scale);
    }
    public static SceneRig.Joint joint(SceneModel.Part p,String style,double t){
        if(style.equals("PATRICK")&&p.group()==1){
            double pitch=-1.12*s((t-.4)/1.3)*(1-s((t-10.5)/.8)),z=p.z()+7;
            return new SceneRig.Joint(p.x(),p.y()*Math.cos(pitch)-z*Math.sin(pitch),-7+p.y()*Math.sin(pitch)+z*Math.cos(pitch),pitch);
        }
        double pitch=0,roll=0;int root=p.group()/100,limb=p.group()%100;
        if(root!=20&&root!=30)return new SceneRig.Joint(p.x(),p.y(),p.z(),0);
        double pivotY=limb<=2?1.08:.64;double pivotX=limb%2==1?-.32:.32;
        if(style.equals("HAND")){pivotY=limb==5?1:1.39;pivotX=limb==5?-.47:-.39+(limb-1)*.25;
            pitch=limb==5?0:1.05*s((t-3.5)/.8);roll=limb==5?-.55*s((t-3.5)/.8):0;
            if(limb==2&&t<3.2)pitch=-.95*s((t-2.25)/.2)*(1-s((t-2.65)/.18));}
        else if(style.equals("PATRICK")||style.equals("SHREK")){
            double running=(style.equals("PATRICK")?t>2&&t<4.8||t>6&&t<9:t>1.4&&t<4.4||t>5.6&&t<8.9)?1:0;
            pitch=Math.sin(t*12+(limb%2)*Math.PI)*.6*running;
            if(limb<=2&&t>(style.equals("PATRICK")?4.8:4.4))pitch=-.8*s((t-4.4)/.5);
            if(style.equals("PATRICK")&&limb<=2){pitch=0;roll=(limb==1?-1:1)*.42*s((t-4.5)/.5);
                if(t>9&&limb==2)roll=.5+.18*Math.sin(t*7);}
        }else if(limb<=2){pitch=s((t-1.5)/.6)*-.75;
            if(style.equals("JUDGE")&&limb==2)pitch=-1.1*s((t-3.4)/.5)+1.45*s((t-4)/.25);
            if(style.equals("MAGICIAN")&&limb==2)pitch=-1.2*s((t-4)/.8)+.35*Math.sin(t*4);
            if(style.equals("FISHER")&&limb==2)pitch=-1.3*s((t-.7)/.5)+1.6*s((t-1.7)/.4);
            if(style.equals("LUIGI")&&limb==2)pitch=-1.0*s((t-2.5)/.5);
        }
        double dx=p.x()-pivotX,dy=p.y()-pivotY,z=p.z();
        // Local Z roll before X pitch matches display leftRotation followed by entity rotation.
        double xx=dx*Math.cos(roll)-dy*Math.sin(roll),yy=dx*Math.sin(roll)+dy*Math.cos(roll);
        return new SceneRig.Joint(pivotX+xx,pivotY+yy*Math.cos(pitch)-z*Math.sin(pitch),yy*Math.sin(pitch)+z*Math.cos(pitch),pitch,roll);
    }
    public record Cue(double time,String sound,float pitch,float volume){}
    public static List<Cue> cues(String style){return switch(style){
        case "PATRICK"->List.of(new Cue(.5,"block.piston.extend",.65f,.8f),new Cue(4.8,"entity.player.attack.sweep",.9f,.6f),new Cue(10.8,"block.anvil.land",.55f,.9f));
        case "SHREK"->List.of(new Cue(.1,"block.wooden_door.open",.8f,.8f),new Cue(4.4,"entity.iron_golem.step",.8f,.7f),new Cue(9.9,"block.wooden_door.close",.7f,.9f));
        case "LUIGI"->List.of(new Cue(1.5,"entity.villager.no",1.3f,.7f),new Cue(2.8,"block.beacon.activate",.6f,.7f),new Cue(6.8,"entity.item.pickup",1.3f,.8f));
        case "POKEBALL"->List.of(new Cue(1,"entity.snowball.throw",1.1f,.7f),new Cue(2.7,"block.beacon.activate",1.5f,.6f),new Cue(4.8,"block.note_block.hat",1,.7f),new Cue(5.6,"block.note_block.hat",1,.7f),new Cue(6.4,"block.note_block.hat",1,.7f),new Cue(7.2,"entity.experience_orb.pickup",1.4f,.9f));
        case "REAPER"->List.of(new Cue(0,"ambient.cave",.7f,.4f),new Cue(2.8,"item.book.page_turn",.8f,.6f),new Cue(4.4,"block.portal.trigger",.7f,.5f));
        case "COURIER"->List.of(new Cue(2.5,"item.book.page_turn",.7f,.6f),new Cue(5.4,"block.chest.close",.8f,.7f),new Cue(7.5,"entity.minecart.riding",1.1f,.5f));
        case "JUDGE"->List.of(new Cue(2.6,"item.book.page_turn",1,.7f),new Cue(4.1,"block.wood.hit",.65f,1),new Cue(4.4,"block.wooden_trapdoor.open",.8f,.9f));
        case "HAND"->List.of(new Cue(1.5,"entity.iron_golem.step",.65f,.8f),new Cue(3.5,"entity.player.attack.knockback",.8f,.8f),new Cue(5.3,"entity.player.attack.sweep",.6f,.9f));
        case "MAGICIAN"->List.of(new Cue(2.6,"block.chest.close",.8f,.7f),new Cue(4.6,"entity.evoker.cast_spell",1.2f,.7f),new Cue(6.4,"entity.chicken.ambient",1.2f,.8f));
        case "FISHER"->List.of(new Cue(1.7,"entity.fishing_bobber.throw",1,.8f),new Cue(2.8,"entity.fishing_bobber.retrieve",1,.8f),new Cue(7.2,"block.chest.close",.8f,.8f));
        default->List.of();};}
    public record Caption(String text,double x,double y,double z,int group,double scale){}
    public static List<Caption> captions(String style,String rule,String name){return switch(style){
        case "SHREK"->List.of(new Caption("ЗАНЯТО",-.32,2.2,-3.90,0,.009));
        case "COURIER"->List.of(new Caption("Кому: "+name,-.22,1.18,.531,5,.006));
        case "JUDGE"->List.of(new Caption("Правило "+rule,-.43,1.18,.24,2001,.008));
        case "REAPER"->List.of(new Caption(name,-.41,1.1,.233,2001,.008));
        case "FISHER"->List.of(new Caption("УЛОВ ДНЯ",-.31,.97,.699,0,.008));
        default->List.of();};}
    public record Burst(double time,String particle,int count,double x,double y,double z){}
    public static List<Burst> bursts(String style){return switch(style){
        case "PATRICK"->List.of(new Burst(10.9,"poof",24,0,.4,-4.9));
        case "SHREK"->List.of(new Burst(4.4,"poof",10,0,.2,0));
        case "LUIGI"->List.of(new Burst(2.8,"soul",16,0,1,0),new Burst(6.8,"end_rod",12,.44,1.1,-1.9));
        case "POKEBALL"->List.of(new Burst(2.8,"end_rod",26,0,.8,0),new Burst(7.2,"happy_villager",15,0,.7,0));
        case "REAPER"->List.of(new Burst(.4,"cloud",22,0,.4,-3.5),new Burst(7.6,"soul",18,0,1,-3.7));
        case "COURIER"->List.of(new Burst(5.5,"poof",10,0,1.8,0));
        case "JUDGE"->List.of(new Burst(4.5,"poof",20,0,.1,0));
        case "HAND"->List.of(new Burst(3.6,"poof",12,0,.9,0));
        case "MAGICIAN"->List.of(new Burst(4.6,"poof",24,0,1,-2.1),new Burst(6.6,"enchant",22,0,.7,-2.1));
        case "FISHER"->List.of(new Burst(2.8,"splash",18,0,.4,0),new Burst(5.5,"splash",14,0,.4,0));
        default->List.of();};}
    private CinematicScenes(){}
}
