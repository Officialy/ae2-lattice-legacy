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
