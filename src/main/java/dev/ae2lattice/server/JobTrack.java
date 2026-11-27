package dev.ae2lattice.server;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import dev.ae2lattice.mixin.CpuAccessor;
import java.util.*;

public final class JobTrack {
    private static final int LIMIT = 256;
    private static final Map<CraftingCPUCluster, Set<ICraftingPatternDetails>> JOBS = new WeakHashMap<>();
    private JobTrack() {}
    public static synchronized void remember(CraftingCPUCluster cpu, ICraftingPatternDetails pattern) {
        Set<ICraftingPatternDetails> set = JOBS.computeIfAbsent(cpu, k -> new LinkedHashSet<>());
        if (set.size() < LIMIT) set.add(pattern);
    }
    public static synchronized void clear(CraftingCPUCluster cpu) { JOBS.remove(cpu); }
    public static synchronized void clearAll() { JOBS.clear(); }
    public static synchronized List<ICraftingPatternDetails> patterns(CraftingCPUCluster cpu) {
        Set<ICraftingPatternDetails> set = JOBS.computeIfAbsent(cpu, k -> new LinkedHashSet<>());
        for (ICraftingPatternDetails p : ((CpuAccessor)(Object)cpu).lattice$getTasks().keySet()) {
            if (set.size() >= LIMIT) break;
            set.add(p);
        }
        return new ArrayList<>(set);
    }
}
