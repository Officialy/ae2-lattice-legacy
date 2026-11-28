package dev.ae2lattice.client;
import dev.ae2lattice.net.Snapshot;
import java.util.*;

/** Client-only illustrative pulses, emitted only on observed CPU counters increasing. */
final class FlowAnimation {
    private static final long LIFE=900000000L;
    private final Map<Integer,Long> start=new HashMap<>();
    private Snapshot previous;
    void reset(){previous=null;start.clear();}
    void accept(Snapshot next,long now){
        if(next==previous)return;
        if(previous==null || next==null || previous.nonce!=next.nonce || previous.page!=next.page ||
            !Objects.equals(previous.cpu,next.cpu) || !Objects.equals(previous.output,next.output) ||
            next.elapsed<previous.elapsed || !sameTopology(previous,next)){
            start.clear();
        }else{
            for(int i=0;i<next.nodes.size();i++){
                Snapshot.Node before=previous.nodes.get(i),after=next.nodes.get(i);
                if(after.returned>before.returned || after.dispatched>before.dispatched)start.put(i,now);
            }
        }
        previous=next;
    }
    private boolean sameTopology(Snapshot a,Snapshot b){
        if(a.nodes.size()!=b.nodes.size())return false;
        for(int i=0;i<a.nodes.size();i++){
            Snapshot.Node x=a.nodes.get(i),y=b.nodes.get(i);
            if(x.itemId!=y.itemId || x.damage!=y.damage || !x.label.equals(y.label) ||
                !Arrays.equals(x.dependencies,y.dependencies))return false;
        }
        return true;
    }
    double phase(int node,long now){
        Long t=start.get(node);
        if(t==null)return -1;
        long age=now-t;
        if(age<0 || age>=LIFE){start.remove(node);return -1;}
        return (double)age/LIFE;
    }
    static int[] point(int ax,int ay,int mid,int bx,int by,double phase){
        double first=Math.abs(mid-ax),second=Math.abs(by-ay),third=Math.abs(bx-mid);
        double d=Math.max(0,Math.min(1,phase))*(first+second+third);
        if(d<=first)return new int[]{ax+(int)(Math.signum(mid-ax)*d),ay};
        d-=first;
        if(d<=second)return new int[]{mid,ay+(int)(Math.signum(by-ay)*d)};
        d-=second;
        return new int[]{mid+(int)(Math.signum(bx-mid)*d),by};
    }
}
