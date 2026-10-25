package dev.aeltorx.inflow.common;

/** Individual limb joints: running, anticipation, baton strike and recovery. */
public final class SceneRig {
    public record Joint(double x,double y,double z,double pitch,double roll) {
        public Joint(double x,double y,double z,double pitch){this(x,y,z,pitch,0);}
    }
    private SceneRig() { }
    private static double smooth(double x){x=Math.clamp(x,0,1);return x*x*x*(x*(x*6-15)+10);}
    public static Joint joint(SceneModel.Part part,String style,double t) {
        if(CinematicScenes.STYLES.contains(style))return CinematicScenes.joint(part,style,t);
        double x=part.x(),y=part.y(),z=part.z(),pitch=0;
        if(style.equals("PORTAL")){double scale=SceneTimeline.pose(style,part.group(),t).scale();x*=scale;y=1.3+(y-1.3)*scale;z*=scale;}
        int limb=part.group()>=200?part.group()%100:0;
        if(style.equals("SPIDER")&&part.group()>=101&&part.group()<=104){
            int g=part.group();double pivotY=g<=102?1.2:.6;
            pitch=t>=1.7&&t<3.3&&g<=102?-1.1*smooth((t-1.7)/.4):
                t>=3.3&&t<6.5?Math.sin((t-3.3)*7+(g%2)*Math.PI)*(g<=102?.9:.45):0;
            double dy=y-pivotY;y=pivotY+dy*Math.cos(pitch)-z*Math.sin(pitch);z=dy*Math.sin(pitch)+z*Math.cos(pitch);
        }
        if(style.equals("MINIONS")&&limb>0) {
            double px=limb==1?-.15:limb==2?.17:limb==3?-.36:.36;
            double py=limb<=2?.3:.85;
            if(t<1.7)pitch=Math.sin(t*16+((limb==5||limb==6?4:limb)%2)*Math.PI)*.55*(1-smooth((t-1.35)/.35));
            else if(limb==4||limb==5||limb==6) {
                double phase=t<2.6?-smooth((t-1.7)/.9):t<3.2?-1+1.6*smooth((t-2.6)/.6):.6*(1-smooth((t-3.2)/.9));
                double upper=phase*.72,elbow=phase*.21,wrist=phase*.07;
                if(limb==5) {
                    double dy=y-.5,dz=z-.19;
                    y=.5+dy*Math.cos(wrist)-dz*Math.sin(wrist);
                    z=.19+dy*Math.sin(wrist)+dz*Math.cos(wrist);
                }
                if(limb==5||limb==6) {
                    double dy=y-.71;
                    y=.71+dy*Math.cos(elbow)-z*Math.sin(elbow);
                    z=dy*Math.sin(elbow)+z*Math.cos(elbow);
                }
                pitch=upper;
                double dy=y-py;
                y=py+dy*Math.cos(upper)-z*Math.sin(upper);
                z=dy*Math.sin(upper)+z*Math.cos(upper);
                return new Joint(x,y,z,upper+(limb==4?0:elbow)+(limb==5?wrist:0));
            }
            double dy=y-py;
            y=py+dy*Math.cos(pitch)-z*Math.sin(pitch);
            z=dy*Math.sin(pitch)+z*Math.cos(pitch);
            x=px+(x-px);
        }
        return new Joint(x,y,z,pitch);
    }
}
