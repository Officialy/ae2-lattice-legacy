package dev.ae2lattice.net;

import java.io.*;

/** Preflight uncompressed, standard 1.7.10 NBT before invoking the vanilla parser. */
public final class BoundedNbt {
    public static final int MAX_BYTES=512;
    private BoundedNbt(){}
    public static boolean valid(byte[] data){
        if(data==null || data.length==0 || data.length>MAX_BYTES)return false;
        try{
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(data));
            if(in.readUnsignedByte()!=10)return false;
            in.readUTF();payload(in,10,0);
            return in.available()==0;
        }catch(IOException ex){return false;}
    }
    private static void skip(DataInputStream in,int n)throws IOException{
        if(n<0 || n>in.available())throw new IOException("Invalid NBT length");
        in.skipBytes(n);
    }
    private static void payload(DataInputStream in,int type,int depth)throws IOException{
        if(depth>12)throw new IOException("NBT nesting");
        switch(type){
            case 1:skip(in,1);break;
            case 2:skip(in,2);break;
            case 3:case 5:skip(in,4);break;
            case 4:case 6:skip(in,8);break;
            case 7:skip(in,in.readInt());break;
            case 8:in.readUTF();break;
            case 9:{
                int kind=in.readUnsignedByte(),count=in.readInt();
                if(count<0 || count>MAX_BYTES || kind>11 || (kind==0 && count!=0))throw new IOException("NBT list");
                for(int i=0;i<count;i++)payload(in,kind,depth+1);
                break;
            }
            case 10:{
                int kind;
                while((kind=in.readUnsignedByte())!=0){in.readUTF();payload(in,kind,depth+1);}
                break;
            }
            case 11:{
                int n=in.readInt();
                if(n<0 || n>in.available()/4)throw new IOException("NBT array");
                skip(in,n*4);break;
            }
            default:throw new IOException("Unsupported NBT tag");
        }
    }
}
