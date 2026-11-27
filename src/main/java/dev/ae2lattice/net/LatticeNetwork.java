package dev.ae2lattice.net;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import dev.ae2lattice.Lattice;
import dev.ae2lattice.server.ServerHooks;
import io.netty.buffer.ByteBuf;
import java.io.IOException;

public final class LatticeNetwork {
    public static final String CHANNEL = "ae2lattice";
    public static SimpleNetworkWrapper channel;
    public static void init() {
        channel=NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(RequestHandler.class,Request.class,0,Side.SERVER);
        channel.registerMessage(ResponseHandler.class,Response.class,1,Side.CLIENT);
    }
    public static final class Request implements IMessage {
        public int window, nonce;
        public boolean valid;
        public Request() {}
        public Request(int window,int nonce) { this.window=window; this.nonce=nonce; valid=true; }
        @Override public void fromBytes(ByteBuf b) {
            if(b.readableBytes()!=9 || b.readUnsignedByte()!=Snapshot.PROTOCOL) return;
            window=b.readInt(); nonce=b.readInt(); valid=true;
        }
        @Override public void toBytes(ByteBuf b) {
            b.writeByte(Snapshot.PROTOCOL); b.writeInt(window); b.writeInt(nonce);
        }
    }
    public static final class RequestHandler implements IMessageHandler<Request,IMessage> {
        @Override public IMessage onMessage(Request message,MessageContext ctx) {
            if(message.valid) ServerHooks.enqueue(ctx.getServerHandler().playerEntity,message);
            return null;
        }
    }
    public static final class Response implements IMessage {
        private byte[] data;
        public Response() {}
        public Response(Snapshot snapshot) throws IOException { data=snapshot.encode(); }
        @Override public void fromBytes(ByteBuf b) {
            int len=b.readableBytes();
            if(len<=0 || len>Snapshot.MAX_BYTES) return;
            data=new byte[len]; b.readBytes(data);
        }
        @Override public void toBytes(ByteBuf b) { if(data!=null) b.writeBytes(data); }
    }
    public static final class ResponseHandler implements IMessageHandler<Response,IMessage> {
        @Override public IMessage onMessage(Response message,MessageContext ctx) {
            if(message.data!=null) try { Lattice.proxy.receive(Snapshot.decode(message.data)); }
            catch(IOException ignored) { /* malformed or incompatible snapshot */ }
            return null;
        }
    }
}
