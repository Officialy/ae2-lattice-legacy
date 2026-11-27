package dev.ae2lattice.client;
import dev.ae2lattice.net.Snapshot;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
public class FlowAnimationTest {
    private Snapshot snap(long returned,long elapsed){
        Snapshot.Node n=new Snapshot.Node("Copper","","",0,0,0,0,0,0,1,0,new int[0],null,0,returned,0,"");
        return new Snapshot(1,2,"CPU","Output","Crafting",elapsed,1,1,false,Collections.singletonList(n));
    }
    @Test public void pulsesOnlyOnNewObservedEvents(){
        FlowAnimation a=new FlowAnimation();
        a.accept(snap(2,10),100);assertEquals(-1,a.phase(0,100),0);
        a.accept(snap(3,20),200);assertEquals(0,a.phase(0,200),0);
        a.accept(snap(3,30),300);assertTrue(a.phase(0,300)>0);
        assertEquals(-1,a.phase(0,1000000200L),0);
    }
    @Test public void resetsOnNewJob(){
        FlowAnimation a=new FlowAnimation();a.accept(snap(1,10),100);a.accept(snap(2,20),200);
        a.accept(snap(0,1),300);assertEquals(-1,a.phase(0,300),0);
    }
    @Test public void pathEndpoints(){
        assertArrayEquals(new int[]{0,0},FlowAnimation.point(0,0,10,20,20,0));
        assertArrayEquals(new int[]{20,20},FlowAnimation.point(0,0,10,20,20,1));
    }
}
