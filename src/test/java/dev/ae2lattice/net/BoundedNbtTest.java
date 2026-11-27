package dev.ae2lattice.net;
import org.junit.Test;
import static org.junit.Assert.*;
public class BoundedNbtTest {
    @Test public void validCompound(){assertTrue(BoundedNbt.valid(new byte[]{10,0,0,1,0,1,120,42,0}));}
    @Test public void rejectsTruncated(){assertFalse(BoundedNbt.valid(new byte[]{10,0,0,1,0,1,120}));}
    @Test public void rejectsLargeArray(){assertFalse(BoundedNbt.valid(new byte[]{10,0,0,7,0,0,127,0,0,0,0}));}
    @Test public void rejectsOversize(){assertFalse(BoundedNbt.valid(new byte[513]));}
    @Test public void rejectsDeepCompound(){
        byte[] data=new byte[3+14*3+15];data[0]=10;
        for(int i=0;i<14;i++)data[3+i*3]=10;
        assertFalse(BoundedNbt.valid(data));
    }
}
