package dev.ae2lattice.net;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SnapshotTest {
    @Test public void roundTrip() throws Exception {
        Snapshot.Node a=new Snapshot.Node("Copper","", "",4,1,2,3,2,8,1,0,new int[0]);
        Snapshot.Node b=new Snapshot.Node("Cable","BLOCKING_MODE","candidate",2,0,5,9,-1,-1,2,0,new int[]{0});
        Snapshot s=new Snapshot(3,7,"CPU","Cable","Crafting",10,20,9,false,Arrays.asList(a,b));
        Snapshot d=Snapshot.decode(s.encode());
        assertEquals(7,d.nonce);assertEquals(2,d.nodes.size());assertEquals(5,d.nodes.get(1).active);
        assertArrayEquals(new int[]{0},d.nodes.get(1).dependencies);
    }
    @Test public void machineRoundTrip() throws Exception {
        Snapshot.Machine machine=new Snapshot.Machine(-1,20,80,-30,123,456,"Copper","Water");
        Snapshot.Node node=new Snapshot.Node("Copper","","",1,2,3,4,5,6,7,8,new int[0],machine);
        Snapshot s=new Snapshot(1,2,"CPU","Output","Crafting",0,0,0,false,Collections.singletonList(node));
        Snapshot.Machine decoded=Snapshot.decode(s.encode()).nodes.get(0).machine;
        assertEquals(-1,decoded.dim);assertEquals(-30,decoded.z);
        assertEquals(123,decoded.energy);assertEquals("Water",decoded.fluids);
    }
    @Test public void dispatchAndHatchesRoundTrip() throws Exception {
        Snapshot.Machine m=new Snapshot.Machine(0,1,2,3,4,5,"","",4);
        Snapshot.Node n=new Snapshot.Node("Copper","","",1,2,3,4,5,6,7,8,new int[0],m,12,34,2,"Observed receiving tile");
        Snapshot s=new Snapshot(1,2,"CPU","Output","Crafting",0,0,0,false,Collections.singletonList(n));
        Snapshot.Node decoded=Snapshot.decode(s.encode()).nodes.get(0);
        assertEquals(12,decoded.dispatched);assertEquals(34,decoded.returned);
        assertEquals(2,decoded.destinations);assertEquals(4,decoded.machine.hatches);
        assertEquals("Observed receiving tile",decoded.route);
    }
    @Test public void pageAndCrossPageDependenciesRoundTrip() throws Exception {
        Snapshot.Node n=new Snapshot.Node("Copper","","",1,2,3,4,5,6,7,8,new int[0],null,12,34,2,"",
            new int[]{0,36},"1x Ore","2x Copper","",true);
        Snapshot s=new Snapshot(1,2,"CPU","Output","Crafting",0,0,0,false,Collections.singletonList(n),1,48);
        Snapshot decoded=Snapshot.decode(s.encode());
        assertEquals(1,decoded.page);assertEquals(4,decoded.pageCount());
        assertArrayEquals(new int[]{0,36},decoded.nodes.get(0).externalDependencies);
        assertTrue(decoded.nodes.get(0).recipeSafe);assertEquals("1x Ore",decoded.nodes.get(0).ingredients);
        assertEquals(3,Snapshot.clampPage(170,48));assertEquals(0,Snapshot.clampPage(9,0));
    }
    @Test public void structuredDetailsRoundTrip()throws Exception{
        byte[] tag={10,0,0,1,0,1,120,42,0};
        Snapshot.Slot slot=new Snapshot.Slot(1,2,3,0,"");
        Snapshot.Slot tank=new Snapshot.Slot(-1,0,500,1000,"water");
        Snapshot.Machine machine=new Snapshot.Machine(0,1,2,3,4,5,"","",1,Collections.singletonList(slot),Collections.singletonList(tank));
        Snapshot.Node node=new Snapshot.Node("Copper","","",1,2,3,4,5,6,7,8,new int[0],machine,0,0,0,"",
            new int[0],"","","",true,new Snapshot.Details(tag,Collections.singletonList(slot),Collections.emptyList()));
        Snapshot s=new Snapshot(1,2,"CPU","Output","Crafting",0,0,0,false,Collections.singletonList(node));
        Snapshot.Node out=Snapshot.decode(s.encode()).nodes.get(0);
        assertArrayEquals(tag,out.details.tag);assertTrue(out.recipeSafe);
        assertEquals(3,out.details.inputs.get(0).amount);assertEquals(1000,out.machine.tanks.get(0).capacity);
    }
    @Test public void worstCasePageFitsPacket() throws Exception {
        char[] chars=new char[64];Arrays.fill(chars,'\u4e00');String label=new String(chars);
        List<Snapshot.Node> nodes=new ArrayList<>();
        for(int i=0;i<Snapshot.PAGE_SIZE;i++){
            Snapshot.Machine m=new Snapshot.Machine(0,1,2,3,4,5,label,label,12);
            nodes.add(new Snapshot.Node(label,label,label,1,2,3,4,5,6,7,8,new int[0],m,12,34,2,label,
                new int[]{24,25,26,27,28,29,30,31,32,33,34,35},label,label,label,true));
        }
        Snapshot s=new Snapshot(1,2,label,label,label,0,0,0,false,nodes,0,48);
        assertEquals(Snapshot.PAGE_SIZE,Snapshot.decode(s.encode()).nodes.size());
    }
    @Test(expected=java.io.IOException.class) public void rejectsOversize() throws Exception {
        Snapshot.decode(new byte[Snapshot.MAX_BYTES+1]);
    }
    @Test(expected=java.io.IOException.class) public void rejectsWrongVersion() throws Exception {
        Snapshot.decode(new byte[]{99});
    }
    @Test public void boundsLargeGraphs() throws Exception {
        List<Snapshot.Node> nodes=new ArrayList<>();
        char[] chars=new char[96];Arrays.fill(chars,'\u4e00');String longLabel=new String(chars);
        for(int i=0;i<180;i++)nodes.add(new Snapshot.Node(longLabel,longLabel,longLabel,1,2,3,4,0,0,0,0,new int[0]));
        Snapshot s=new Snapshot(1,1,"","","",0,0,0,false,nodes);
        byte[] bytes=s.encode();
        assertTrue(bytes.length<=Snapshot.MAX_BYTES);
        assertTrue(Snapshot.decode(bytes).truncated);
    }
}
