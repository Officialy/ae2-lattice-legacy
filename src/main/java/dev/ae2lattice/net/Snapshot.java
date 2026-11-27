package dev.ae2lattice.net;

import java.io.*;
import java.util.*;

/** No object deserialization, NBT or coordinates supplied by clients. */
public final class Snapshot {
    public static final int PROTOCOL = 4;
    public static final int PAGE_SIZE = 12, MAX_TRACKED = 2048;
    public static final int MAX_PAGE = (MAX_TRACKED-1)/PAGE_SIZE;
    public static final int MAX_BYTES = 24576;
    public static final int MAX_NODES = PAGE_SIZE;
    public static final int MAX_EDGES = 12;
    public final int window, nonce, page, totalNodes;
    public final String cpu, output, status;
    public final long elapsed, total, remaining;
    public final boolean truncated;
    public final List<Node> nodes;

    public Snapshot(int window, int nonce, String cpu, String output, String status,
                    long elapsed, long total, long remaining, boolean truncated, List<Node> nodes) {
        this(window,nonce,cpu,output,status,elapsed,total,remaining,truncated,nodes,0,nodes.size());
    }
    public Snapshot(int window,int nonce,String cpu,String output,String status,long elapsed,long total,long remaining,
                    boolean truncated,List<Node> nodes,int page,int totalNodes){
        this.page=page;this.totalNodes=Math.min(MAX_TRACKED,totalNodes);
        this.window=window; this.nonce=nonce; this.cpu=cpu; this.output=output; this.status=status;
        this.elapsed=elapsed; this.total=total; this.remaining=remaining; this.truncated=truncated;
        this.nodes=Collections.unmodifiableList(new ArrayList<>(nodes));
    }
    public int pageCount(){return Math.max(1,(totalNodes+PAGE_SIZE-1)/PAGE_SIZE);}
    public static int clampPage(int page,int totalNodes){return Math.max(0,Math.min(page,Math.max(0,(totalNodes-1)/PAGE_SIZE)));}
    /** Bounded, owner-authorized observation of a loaded controller/tile. */
    public static final class Machine {
        public final int dim,x,y,z;
        public final long energy,capacity;
        public final int hatches;
        public final String inventory,fluids;
        public Machine(int dim,int x,int y,int z,long energy,long capacity,String inventory,String fluids){
            this(dim,x,y,z,energy,capacity,inventory,fluids,0);
        }
        public Machine(int dim,int x,int y,int z,long energy,long capacity,String inventory,String fluids,int hatches){
            this.hatches=hatches;
            this.dim=dim;this.x=x;this.y=y;this.z=z;this.energy=energy;this.capacity=capacity;
            this.inventory=inventory;this.fluids=fluids;
        }
    }
    public static final class Node {
        public final String label, reason, provider;
        public final long runs, stored, active, pending, dispatched, returned;
        public final int destinations;
        public final String route;
        public final int progress, maxProgress, itemId, damage;
        public final int[] dependencies, externalDependencies;
        public final String ingredients, products, fluidName;
        public final boolean recipeSafe;
        public final Machine machine;
        public Node(String label, String reason, String provider, long runs, long stored, long active, long pending,
                    int progress, int maxProgress, int itemId, int damage, int[] dependencies) {
            this(label,reason,provider,runs,stored,active,pending,progress,maxProgress,itemId,damage,dependencies,null);
        }
        public Node(String label,String reason,String provider,long runs,long stored,long active,long pending,
                    int progress,int maxProgress,int itemId,int damage,int[] dependencies,Machine machine){
            this(label,reason,provider,runs,stored,active,pending,progress,maxProgress,itemId,damage,dependencies,machine,0,0,0,"");
        }
        public Node(String label,String reason,String provider,long runs,long stored,long active,long pending,
                    int progress,int maxProgress,int itemId,int damage,int[] dependencies,Machine machine,
                    long dispatched,long returned,int destinations,String route){
            this(label,reason,provider,runs,stored,active,pending,progress,maxProgress,itemId,damage,dependencies,machine,
                dispatched,returned,destinations,route,new int[0],"","","",false);
        }
        public Node(String label,String reason,String provider,long runs,long stored,long active,long pending,
                    int progress,int maxProgress,int itemId,int damage,int[] dependencies,Machine machine,
                    long dispatched,long returned,int destinations,String route,int[] externalDependencies,
                    String ingredients,String products,String fluidName,boolean recipeSafe){
            this.externalDependencies=externalDependencies.clone();this.ingredients=ingredients;this.products=products;
            this.fluidName=fluidName;this.recipeSafe=recipeSafe;
            this.dispatched=dispatched;this.returned=returned;this.destinations=destinations;this.route=route;
            this.machine=machine;
            this.label=label; this.reason=reason; this.provider=provider; this.runs=runs; this.stored=stored;
            this.active=active; this.pending=pending; this.progress=progress; this.maxProgress=maxProgress;
            this.itemId=itemId; this.damage=damage; this.dependencies=dependencies.clone();
        }
    }
    private static void string(DataOutputStream out, String value) throws IOException {
        if (value == null) value = "";
        out.writeUTF(value.substring(0, Math.min(value.length(), 64)));
    }
    public byte[] encode() throws IOException {
        int count = Math.min(Math.min(nodes.size(), MAX_NODES),Math.max(0,totalNodes-page*PAGE_SIZE));
        while (true) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(PROTOCOL); out.writeInt(window); out.writeInt(nonce);
            string(out,cpu); string(out,output); string(out,status);
            out.writeLong(elapsed); out.writeLong(total); out.writeLong(remaining);
            out.writeBoolean(truncated || count < nodes.size()); out.writeShort(page);out.writeShort(totalNodes);out.writeShort(count);
            for (int i=0;i<count;i++) {
                Node n=nodes.get(i);
                string(out,n.label); string(out,n.reason); string(out,n.provider);
                out.writeLong(n.runs); out.writeLong(n.stored); out.writeLong(n.active); out.writeLong(n.pending);
                out.writeInt(n.progress); out.writeInt(n.maxProgress); out.writeInt(n.itemId); out.writeInt(n.damage);
                out.writeLong(n.dispatched);out.writeLong(n.returned);out.writeByte(n.destinations);string(out,n.route);
                out.writeBoolean(n.machine!=null);
                if(n.machine!=null){
                    Machine m=n.machine;
                    out.writeInt(m.dim);out.writeInt(m.x);out.writeInt(m.y);out.writeInt(m.z);
                    out.writeLong(m.energy);out.writeLong(m.capacity);string(out,m.inventory);string(out,m.fluids);out.writeByte(m.hatches);
                }
                string(out,n.ingredients);string(out,n.products);string(out,n.fluidName);out.writeBoolean(n.recipeSafe);
                int cross=0;
                for(int d:n.externalDependencies)if(d>=0 && d<totalNodes && d/PAGE_SIZE!=page && cross<MAX_EDGES)cross++;
                out.writeByte(cross);
                for(int d:n.externalDependencies)if(d>=0 && d<totalNodes && d/PAGE_SIZE!=page && cross-->0)out.writeShort(d);
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
        int page=in.readUnsignedShort(),totalNodes=in.readUnsignedShort(),count=in.readUnsignedShort();
        if(totalNodes>MAX_TRACKED || page>MAX_PAGE || page!=clampPage(page,totalNodes) || count>Math.max(0,totalNodes-page*PAGE_SIZE))throw new IOException("Invalid page");
        if(count>MAX_NODES) throw new IOException("Too many nodes");
        List<Node> nodes=new ArrayList<>();
        for(int i=0;i<count;i++){
            String label=in.readUTF(), reason=in.readUTF(), provider=in.readUTF();
            long runs=in.readLong(), stored=in.readLong(), active=in.readLong(), pending=in.readLong();
            int progress=in.readInt(), max=in.readInt(), itemId=in.readInt(), damage=in.readInt();
            long dispatched=in.readLong(),returned=in.readLong();int destinations=in.readUnsignedByte();String route=in.readUTF();
            if(dispatched<0 || returned<0 || destinations>8)throw new IOException("Invalid event counters");
            Machine machine=null;
            if(in.readBoolean())machine=new Machine(in.readInt(),in.readInt(),in.readInt(),in.readInt(),
                in.readLong(),in.readLong(),in.readUTF(),in.readUTF(),in.readUnsignedByte());
            String ingredients=in.readUTF(),products=in.readUTF(),fluidName=in.readUTF();boolean recipeSafe=in.readBoolean();
            int cross=in.readUnsignedByte();
            if(cross>MAX_EDGES)throw new IOException("Too many external edges");
            int[] external=new int[cross];
            for(int j=0;j<cross;j++){
                external[j]=in.readUnsignedShort();
                if(external[j]>=totalNodes || external[j]/PAGE_SIZE==page)throw new IOException("Invalid external dependency");
            }
            int edges=in.readUnsignedByte();
            if(edges>MAX_EDGES) throw new IOException("Too many edges");
            int[] deps=new int[edges];
            for(int j=0;j<edges;j++) {
                deps[j]=in.readUnsignedShort();
                if(deps[j]>=count || deps[j]==i) throw new IOException("Invalid edge");
            }
            nodes.add(new Node(label,reason,provider,runs,stored,active,pending,progress,max,itemId,damage,deps,machine,dispatched,returned,destinations,route,external,ingredients,products,fluidName,recipeSafe));
        }
        if(in.available()!=0) throw new IOException("Trailing snapshot bytes");
        return new Snapshot(window,nonce,cpu,output,status,elapsed,total,remaining,truncated,nodes,page,totalNodes);
    }
}
