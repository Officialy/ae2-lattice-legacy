package dev.ae2lattice.server;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.NamedDimensionalCoord;
import appeng.me.helpers.IGridProxyable;
import appeng.helpers.DualityInterface;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import dev.ae2lattice.mixin.CpuAccessor;
import java.util.*;

public final class JobTrack {
    private static final int LIMIT = 256;
    private static final Map<CraftingCPUCluster, Set<ICraftingPatternDetails>> JOBS = new WeakHashMap<>();
    private static final Map<CraftingCPUCluster, Map<ICraftingPatternDetails, Dispatch>> DISPATCHES = new WeakHashMap<>();
    private JobTrack() {}
    public static final class Dispatch {
        final NamedDimensionalCoord provider;
        final long count;
        Dispatch(NamedDimensionalCoord provider,long count){this.provider=provider;this.count=count;}
    }

    // Observe successful handoffs only; never retain a tile or crafting medium.
    public static synchronized void dispatched(CraftingCPUCluster cpu, ICraftingPatternDetails pattern, ICraftingMedium medium) {
        remember(cpu,pattern);
        if(!JOBS.get(cpu).contains(pattern)) return;
        Map<ICraftingPatternDetails,Dispatch> map=DISPATCHES.computeIfAbsent(cpu,k->new HashMap<>());
        Dispatch previous=map.get(pattern);
        long count=previous==null?1:previous.count==Long.MAX_VALUE?Long.MAX_VALUE:previous.count+1;
        NamedDimensionalCoord provider=null;
        try {
            if(medium instanceof IGridProxyable){
                DimensionalCoord loc=((IGridProxyable)medium).getLocation();
                String name=medium instanceof DualityInterface?((DualityInterface)medium).getRawTermName():
                    medium.getClass().getSimpleName();
                if(loc!=null)provider=new NamedDimensionalCoord(loc,name);
            }
        } catch(RuntimeException ignored) { /* Optional metadata. */ }
        map.put(pattern,new Dispatch(provider,count));
    }
    public static synchronized Dispatch latest(CraftingCPUCluster cpu,ICraftingPatternDetails pattern){
        Map<ICraftingPatternDetails,Dispatch> map=DISPATCHES.get(cpu);
        return map==null?null:map.get(pattern);
    }
    public static synchronized void remember(CraftingCPUCluster cpu, ICraftingPatternDetails pattern) {
        Set<ICraftingPatternDetails> set = JOBS.computeIfAbsent(cpu, k -> new LinkedHashSet<>());
        if (set.size() < LIMIT) set.add(pattern);
    }
    public static synchronized void clear(CraftingCPUCluster cpu) { JOBS.remove(cpu); DISPATCHES.remove(cpu); }
    public static synchronized void clearAll() { JOBS.clear(); DISPATCHES.clear(); }
    public static synchronized List<ICraftingPatternDetails> patterns(CraftingCPUCluster cpu) {
        Set<ICraftingPatternDetails> set = JOBS.computeIfAbsent(cpu, k -> new LinkedHashSet<>());
        for (ICraftingPatternDetails p : ((CpuAccessor)(Object)cpu).lattice$getTasks().keySet()) {
            if (set.size() >= LIMIT) break;
            set.add(p);
        }
        return new ArrayList<>(set);
    }
}
