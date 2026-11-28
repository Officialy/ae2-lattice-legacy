package dev.ae2lattice.server;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.NamedDimensionalCoord;
import appeng.me.helpers.IGridProxyable;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.api.storage.data.IAEStack;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import dev.ae2lattice.mixin.CpuAccessor;
import java.util.*;
import java.lang.ref.WeakReference;
import dev.ae2lattice.net.Snapshot;

public final class JobTrack {
    private static final int LIMIT = dev.ae2lattice.net.Snapshot.MAX_TRACKED;
    private static final Map<CraftingCPUCluster, Set<ICraftingPatternDetails>> JOBS = new WeakHashMap<>();
    private static final Map<CraftingCPUCluster, Map<ICraftingPatternDetails, Dispatch>> DISPATCHES = new WeakHashMap<>();
    private static final Map<CraftingCPUCluster, Finished> FINISHED = new WeakHashMap<>();
    private static final Map<CraftingCPUCluster, String> OUTPUTS = new WeakHashMap<>();
    private static final Map<CraftingCPUCluster, Map<IAEStack<?>,Long>> RETURNS=new WeakHashMap<>();
    private static final Map<CraftingCPUCluster,Topology> TOPOLOGY=new WeakHashMap<>();
    private static final long SHARED_WINDOW=60L*1000000000L;
    private static final Map<String,Touch> TOUCHES=new LinkedHashMap<String,Touch>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Touch> e){return size()>4096;}
    };
    private static final class Touch {
        final WeakReference<CraftingCPUCluster> cpu;
        final WeakReference<ICraftingPatternDetails> pattern;
        final long at,conflict;
        Touch(CraftingCPUCluster cpu,ICraftingPatternDetails pattern,long at,long conflict){
            this.cpu=new WeakReference<>(cpu);this.pattern=new WeakReference<>(pattern);this.at=at;this.conflict=conflict;
        }
        boolean matches(CraftingCPUCluster cpu,ICraftingPatternDetails pattern){
            return this.cpu.get()==cpu && Objects.equals(this.pattern.get(),pattern);
        }
    }
    private static String coordinate(NamedDimensionalCoord c){return c.getDimension()+":"+c.x+":"+c.y+":"+c.z;}
    private static void touch(CraftingCPUCluster cpu,ICraftingPatternDetails pattern,NamedDimensionalCoord c){
        String key=coordinate(c);long now=System.nanoTime();Touch prior=TOUCHES.get(key);
        long conflict=prior==null?Long.MIN_VALUE:prior.conflict;
        if(prior!=null && now-prior.at<SHARED_WINDOW && !prior.matches(cpu,pattern))conflict=now;
        TOUCHES.put(key,new Touch(cpu,pattern,now,conflict));
    }
    static synchronized boolean shared(CraftingCPUCluster cpu,ICraftingPatternDetails pattern,Dispatch dispatch,Snapshot.Machine machine){
        if(dispatch==null)return false;
        long now=System.nanoTime();
        List<NamedDimensionalCoord> coords=new ArrayList<>(dispatch.destinations);
        coords.add(new NamedDimensionalCoord(machine.x,machine.y,machine.z,machine.dim,""));
        for(NamedDimensionalCoord coord:coords){
            Touch t=TOUCHES.get(coordinate(coord));
            if(t!=null && now-t.at<SHARED_WINDOW && (!t.matches(cpu,pattern) ||
                (t.conflict!=Long.MIN_VALUE && now-t.conflict<SHARED_WINDOW)))return true;
        }
        return false;
    }
    private JobTrack() {}
    private static IAEStack<?> key(IAEStack<?> stack){
        IAEStack<?> copy=stack.copy();copy.setStackSize(1);return copy;
    }
    public static synchronized void returned(CraftingCPUCluster cpu,IAEStack<?> stack){
        if(stack==null || stack.getStackSize()<=0)return;
        Map<IAEStack<?>,Long> counts=RETURNS.computeIfAbsent(cpu,k->new HashMap<>());
        IAEStack<?> key=key(stack);
        if(!counts.containsKey(key) && counts.size()>=512)return;
        long previous=counts.getOrDefault(key,0L), amount=stack.getStackSize();
        counts.put(key,previous>Long.MAX_VALUE-amount?Long.MAX_VALUE:previous+amount);
    }
    static synchronized long returnedCount(CraftingCPUCluster cpu,IAEStack<?> stack){
        Map<IAEStack<?>,Long> counts=RETURNS.get(cpu);
        return counts==null?0:counts.getOrDefault(key(stack),0L);
    }
    static final class Finished {
        final String output,status;
        final long elapsed,total,remaining,at;
        Finished(String output,String status,long elapsed,long total,long remaining){
            this.output=output;this.status=status;this.elapsed=elapsed;this.total=total;this.remaining=remaining;
            this.at=System.nanoTime();
        }
    }
    static synchronized void observeOutput(CraftingCPUCluster cpu,IAEStack<?> output){
        if(output!=null) OUTPUTS.put(cpu,output.getDisplayName());
    }
    public static synchronized void finish(CraftingCPUCluster cpu,boolean canceled){
        if(!JOBS.containsKey(cpu) && !cpu.isBusy())return;
        IAEStack<?> output=cpu.getFinalMultiOutput();
        String name=OUTPUTS.get(cpu);
        if(name==null && output!=null)name=output.getDisplayName();
        FINISHED.put(cpu,new Finished(name==null?"Crafting job":name,canceled?"Canceled":"Completed",
            cpu.getElapsedTime(),cpu.getStartItemCount(),canceled?cpu.getRemainingItemCount():0));
        clear(cpu);
    }
    static synchronized Finished finished(CraftingCPUCluster cpu){
        Finished value=FINISHED.get(cpu);
        if(value!=null && System.nanoTime()-value.at>30L*60*1000000000L){
            FINISHED.remove(cpu);return null;
        }
        return value;
    }
    public static final class Dispatch {
        final NamedDimensionalCoord provider;
        final long count;
        final int targetMask;
        final List<NamedDimensionalCoord> destinations;
        final boolean partial;
        Dispatch(NamedDimensionalCoord provider,long count,int targetMask,List<NamedDimensionalCoord> destinations,boolean partial){
            this.provider=provider;this.count=count;this.targetMask=targetMask;
            this.destinations=Collections.unmodifiableList(new ArrayList<>(destinations));this.partial=partial;
        }
    }

    // Observe successful handoffs only; never retain a tile or crafting medium.
    public static synchronized void dispatched(CraftingCPUCluster cpu, ICraftingPatternDetails pattern, ICraftingMedium medium,DispatchContext context) {
        remember(cpu,pattern);
        if(!JOBS.get(cpu).contains(pattern)) return;
        Map<ICraftingPatternDetails,Dispatch> map=DISPATCHES.computeIfAbsent(cpu,k->new HashMap<>());
        Dispatch previous=map.get(pattern);
        long count=previous==null?1:previous.count==Long.MAX_VALUE?Long.MAX_VALUE:previous.count+1;
        NamedDimensionalCoord provider=null;
        int targetMask=0;
        try {
            if(medium instanceof IGridProxyable){
                DimensionalCoord loc=((IGridProxyable)medium).getLocation();
                // Do not call getRawTermName(): it may inspect neighboring tiles on every push.
                if(loc!=null)provider=new NamedDimensionalCoord(loc,medium instanceof DualityInterface?
                    "ME Interface":medium.getClass().getSimpleName());
                if(medium instanceof DualityInterface){
                    Object host=((DualityInterface)medium).getHost();
                    if(host instanceof IInterfaceHost)
                        for(ForgeDirection side:((IInterfaceHost)host).getTargets())
                            if(side!=ForgeDirection.UNKNOWN)targetMask|=1<<side.ordinal();
                }
            }
        } catch(RuntimeException ignored) { /* Optional metadata. */ }
        List<NamedDimensionalCoord> targets=context.destinations();
        for(NamedDimensionalCoord target:targets)touch(cpu,pattern,target);
        if(targets.isEmpty() && provider!=null)touch(cpu,pattern,provider);
        map.put(pattern,new Dispatch(provider,count,targetMask,targets,context.partial()));
    }
    public static synchronized Dispatch latest(CraftingCPUCluster cpu,ICraftingPatternDetails pattern){
        Map<ICraftingPatternDetails,Dispatch> map=DISPATCHES.get(cpu);
        return map==null?null:map.get(pattern);
    }
    public static synchronized void remember(CraftingCPUCluster cpu, ICraftingPatternDetails pattern) {
        FINISHED.remove(cpu);
        Set<ICraftingPatternDetails> set = JOBS.computeIfAbsent(cpu, k -> new LinkedHashSet<>());
        if (set.size() < LIMIT && set.add(pattern)) TOPOLOGY.remove(cpu);
    }
    public static synchronized void clear(CraftingCPUCluster cpu) { JOBS.remove(cpu); DISPATCHES.remove(cpu); OUTPUTS.remove(cpu); RETURNS.remove(cpu); TOPOLOGY.remove(cpu); }
    public static synchronized void clearAll() { JOBS.clear(); DISPATCHES.clear(); FINISHED.clear(); OUTPUTS.clear(); RETURNS.clear(); TOPOLOGY.clear(); TOUCHES.clear(); }
    public static synchronized List<ICraftingPatternDetails> patterns(CraftingCPUCluster cpu) {
        Set<ICraftingPatternDetails> set = JOBS.computeIfAbsent(cpu, k -> new LinkedHashSet<>());
        for (ICraftingPatternDetails p : ((CpuAccessor)(Object)cpu).lattice$getTasks().keySet()) {
            if (set.size() >= LIMIT) break;
            if(set.add(p))TOPOLOGY.remove(cpu);
        }
        return new ArrayList<>(set);
    }
    static final class Topology {
        final List<ICraftingPatternDetails> patterns;
        final Map<IAEStack<?>,List<Integer>> producers=new HashMap<>();
        Topology(List<ICraftingPatternDetails> patterns){
            this.patterns=patterns;
            for(int i=0;i<patterns.size();i++)
                for(IAEStack<?> out:patterns.get(i).getCondensedAEOutputs())
                    if(out!=null)producers.computeIfAbsent(key(out),k->new ArrayList<>()).add(i);
        }
    }
    static synchronized Topology topology(CraftingCPUCluster cpu){
        // Restored jobs may have tasks not observed through the add hook.
        Topology existing=TOPOLOGY.get(cpu);
        if(existing!=null)return existing;
        Topology value=new Topology(patterns(cpu));TOPOLOGY.put(cpu,value);return value;
    }
}
