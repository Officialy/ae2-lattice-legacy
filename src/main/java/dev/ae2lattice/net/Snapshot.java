package dev.ae2lattice.net;

import java.io.*;
import java.util.*;

/** No object deserialization, NBT or coordinates supplied by clients. */
public final class Snapshot {
    public static final int PROTOCOL = 1;
    public static final int MAX_BYTES = 24576;
    public static final int MAX_NODES = 96;
    public static final int MAX_EDGES = 12;
    public final int window, nonce;
    public final String cpu, output, status;
    public final long elapsed, total, remaining;
    public final boolean truncated;
    public final List<Node> nodes;

    public Snapshot(int window, int nonce, String cpu, String output, String status,
                    long elapsed, long total, long remaining, boolean truncated, List<Node> nodes) {
        this.window=window; this.nonce=nonce; this.cpu=cpu; this.output=output; this.status=status;
        this.elapsed=elapsed; this.total=total; this.remaining=remaining; this.truncated=truncated;
        this.nodes=Collections.unmodifiableList(new ArrayList<>(nodes));
    }
    public static final class Node {
        public final String label, reason, provider;
        public final long runs, stored, active, pending;
        public final int progress, maxProgress, itemId, damage;
        public final int[] dependencies;
        public Node(String label, String reason, String provider, long runs, long stored, long active, long pending,
                    int progress, int maxProgress, int itemId, int damage, int[] dependencies) {
            this.label=label; this.reason=reason; this.provider=provider; this.runs=runs; this.stored=stored;
            this.active=active; this.pending=pending; this.progress=progress; this.maxProgress=maxProgress;
            this.itemId=itemId; this.damage=damage; this.dependencies=dependencies.clone();
        }
    }
    private static void string(DataOutputStream out, String value) throws IOException {
        if (value == null) value = "";
        out.writeUTF(value.substring(0, Math.min(value.length(), 96)));
    }
    public byte[] encode() throws IOException {
        int count = Math.min(nodes.size(), MAX_NODES);
        while (true) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(PROTOCOL); out.writeInt(window); out.writeInt(nonce);
            string(out,cpu); string(out,output); string(out,status);
            out.writeLong(elapsed); out.writeLong(total); out.writeLong(remaining);
            out.writeBoolean(truncated || count < nodes.size()); out.writeShort(count);
            for (int i=0;i<count;i++) {
                Node n=nodes.get(i);
                string(out,n.label); string(out,n.reason); string(out,n.provider);
                out.writeLong(n.runs); out.writeLong(n.stored); out.writeLong(n.active); out.writeLong(n.pending);
                out.writeInt(n.progress); out.writeInt(n.maxProgress); out.writeInt(n.itemId); out.writeInt(n.damage);
                int edges=0;
                for(int d:n.dependencies) if(d>=0 && d<count && d!=i && edges<MAX_EDGES) edges++;
                out.writeByte(edges);
                for(int d:n.dependencies) if(d>=0 && d<count && d!=i && edges-- >0) out.writeShort(d);
            }
            out.flush();
            if (bytes.size() <= MAX_BYTES) return bytes.toByteArray();
            if(count==0) throw new IOException("Oversize header");
            count=Math.max(0,count-8);
        }
    }
    public static Snapshot decode(byte[] bytes) throws IOException {
        if(bytes.length>MAX_BYTES) throw new IOException("Oversize snapshot");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readUnsignedByte()!=PROTOCOL) throw new IOException("Unsupported protocol");
        int window=in.readInt(), nonce=in.readInt();
        String cpu=in.readUTF(), output=in.readUTF(), status=in.readUTF();
        long elapsed=in.readLong(), total=in.readLong(), remaining=in.readLong();
        boolean truncated=in.readBoolean();
        int count=in.readUnsignedShort();
        if(count>MAX_NODES) throw new IOException("Too many nodes");
        List<Node> nodes=new ArrayList<>();
        for(int i=0;i<count;i++){
            String label=in.readUTF(), reason=in.readUTF(), provider=in.readUTF();
            long runs=in.readLong(), stored=in.readLong(), active=in.readLong(), pending=in.readLong();
            int progress=in.readInt(), max=in.readInt(), itemId=in.readInt(), damage=in.readInt();
            int edges=in.readUnsignedByte();
            if(edges>MAX_EDGES) throw new IOException("Too many edges");
            int[] deps=new int[edges];
            for(int j=0;j<edges;j++) {
                deps[j]=in.readUnsignedShort();
                if(deps[j]>=count || deps[j]==i) throw new IOException("Invalid edge");
            }
            nodes.add(new Node(label,reason,provider,runs,stored,active,pending,progress,max,itemId,damage,deps));
        }
        if(in.available()!=0) throw new IOException("Trailing snapshot bytes");
        return new Snapshot(window,nonce,cpu,output,status,elapsed,total,remaining,truncated,nodes);
    }
}
