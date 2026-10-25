package dev.aeltorx.inflow.client;

/** Frame-rate independent, C2-continuous welcome choreography. */
public final class InFlowMotion {
    public static final double DURATION=1.15;
    public record Frame(double logoAlpha,double welcomeAlpha,double backgroundAlpha,double scale,double logoOffset,double welcomeOffset,double spinnerAngle,double spinnerAlpha,boolean done){}
    public static double ease(double t){t=Math.clamp(t,0,1);return Math.clamp(t*t*t*(t*(t*6-15)+10),0,1);}
    public static Frame at(double seconds){double t=Math.max(0,seconds),in=ease(t/.2),greet=ease((t-.12)/.25),out=1-ease((t-.99)/.16);return new Frame(in*out,greet*out,1,.97+.03*in,4*(1-in),4*(1-greet),0,0,t>=DURATION);}
    /** Five petals unfold clockwise, each with its own continuous opacity/scale curve. */
    public static double petal(double seconds,int index){if(index<0||index>=5)throw new IllegalArgumentException("Petal index");return ease((seconds-.04-index*.065)/.24);}
    public static double flowerCenter(double seconds){return ease((seconds-.36)/.18);}
    public static double exitOffset(double seconds){return -14*ease((seconds-.91)/.24);}
    public static final class Session {
        private Object connection;private java.util.UUID player;private boolean shown;
        public boolean begin(Object next,java.util.UUID who){if(next==null||who==null)return false;if(connection!=next||!who.equals(player)){connection=next;player=who;shown=false;}if(shown)return false;shown=true;return true;}
        public void reset(){connection=null;player=null;shown=false;}
    }
}
