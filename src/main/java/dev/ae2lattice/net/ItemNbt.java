package dev.ae2lattice.net;
import java.io.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.CompressedStreamTools;

public final class ItemNbt {
    private ItemNbt(){}
    /** null = not representable; empty = no tag. Never compress or trust an arbitrary length. */
    public static byte[] encode(NBTTagCompound tag){
        if(tag==null)return new byte[0];
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        OutputStream capped=new OutputStream(){
            @Override public void write(int b)throws IOException{
                if(bytes.size()>=BoundedNbt.MAX_BYTES)throw new IOException("NBT too large");
                bytes.write(b);
            }
            @Override public void write(byte[] b,int off,int len)throws IOException{
                if(len>BoundedNbt.MAX_BYTES-bytes.size())throw new IOException("NBT too large");
                bytes.write(b,off,len);
            }
        };
        try{
            CompressedStreamTools.write(tag,new DataOutputStream(capped));
            byte[] result=bytes.toByteArray();
            return BoundedNbt.valid(result)?result:null;
        }catch(IOException|RuntimeException|StackOverflowError ex){return null;}
    }
    public static NBTTagCompound decode(byte[] bytes)throws IOException{
        if(bytes.length==0)return null;
        if(!BoundedNbt.valid(bytes))throw new IOException("Invalid NBT");
        return CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)),new NBTSizeTracker(16384L));
    }
}
