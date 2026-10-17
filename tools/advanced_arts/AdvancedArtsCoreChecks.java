import com.example.myvillage.combat.art.ArtMath;
import com.example.myvillage.combat.art.ArtCooldowns;
import com.example.myvillage.client.combat.ArtRibbonShape;
import java.util.*;

/** Runs production pure Java classes without Minecraft/Gradle; NOT a mod build. */
public final class AdvancedArtsCoreChecks {
    private static int checks;
    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    private static void near(double a,double b,String name) { check(Math.abs(a-b)<1e-8,name); }
    private static void invalid(Runnable action) {
        checks++;
        try { action.run(); throw new AssertionError("Expected invalid input rejection"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        for(int i=-256;i<=256;i++) check(ArtMath.validSlot(i)==(i==0||i==1),"slot "+i);
        near(ArtMath.forwardX(0),0,"south x");near(ArtMath.forwardZ(0),1,"south z");
        near(ArtMath.forwardX(90),-1,"west x");near(ArtMath.forwardZ(90),0,"west z");
        for(int yaw=0;yaw<360;yaw+=15) {
            double dx=ArtMath.forwardX(yaw),dz=ArtMath.forwardZ(yaw);
            check(ArtMath.inArc(dx,dz,yaw,160),"front inside guard");
            check(!ArtMath.inArc(-dx,-dz,yaw,160),"back outside guard");
            check(!ArtMath.inArc(dz,-dx,yaw,160),"side outside guard");
            check(ArtMath.inArc(-dx,-dz,yaw,360),"ring includes back");
        }
        check(ArtMath.inReach(0,0,3.9,-.3,4.5,.3,4),"AABB edge not centre");
        check(!ArtMath.inReach(0,0,4.01,-.3,4.5,.3,4),"outside radius");
        check(!ArtMath.inReach(0,0,3.5,3.5,4,4,4),"broad-phase corner excluded");
        for(int age=-1;age<20;age++) {
            check(ArtMath.guarding(0,age,4,.65)==(age>=0&&age<4),"ward window");
            check(!ArtMath.guarding(1,age,4,.65),"no subsequent phase ward");
            check(!ArtMath.guarding(0,age,4,0),"offensive art no ward");
        }
        var ledger=new ArtCooldowns(); UUID p=new UUID(0,1),other=new UUID(0,2);
        ledger.start(p,"art0",100,160);
        check(ledger.remaining(p,"art0",100)==160,"start");
        check(ledger.remaining(p,"art0",259)==1,"last tick");
        check(ledger.ready(p,"art0",260),"exact expiry");
        check(ledger.ready(other,"art0",100),"other player");
        check(ledger.ready(p,"art1",100),"other art");
        ledger.start(p,"art1",150,200);ledger.prune(260);
        check(ledger.remaining(p,"art1",260)==90,"prune preserves other art");
        check(ledger.ready(p,"art0",260),"pruned");
        ledger.clear();check(ledger.ready(p,"art1",261),"server-stop reset");
        check(ArtMath.deadline(Long.MAX_VALUE-5,160)==Long.MAX_VALUE,"overflow saturated");
        check(ArtMath.remaining(Long.MIN_VALUE,Long.MAX_VALUE)==Integer.MAX_VALUE,"remaining saturated");
        invalid(() -> ArtMath.deadline(0,-1));
        for(String fx:List.of("arc","helix","ring","lotus","shock")) {
            for(int count:List.of(8,36,64)) {
                var mesh=ArtRibbonShape.mesh(fx,4.8,360,1,count,false);
                check(mesh.size()==count*(fx.equals("shock")?3:2),"bounded mesh count");
                for(var q:mesh) for(var v:List.of(q.a(),q.b(),q.c(),q.d())) {
                    check(Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z()),"finite vertex");
                    check(Math.hypot(v.x(),v.z())<=4.8+1e-8,"vertex stays in horizontal envelope");
                    check(v.y()>=0&&v.y()<=1.25,"bounded height");
                }
            }
        }
        check(ArtRibbonShape.mesh("arc",4,160,1,36,true).size()==36,"ward ring count");
        invalid(() -> ArtRibbonShape.mesh("wrong",4,360,1,36,false));
        invalid(() -> ArtRibbonShape.mesh("arc",Double.NaN,360,1,36,false));
        invalid(() -> ArtRibbonShape.mesh("arc",4,360,1,100000,false));
        System.out.println("Advanced arts pure-domain checks passed: "+checks);
    }
}
