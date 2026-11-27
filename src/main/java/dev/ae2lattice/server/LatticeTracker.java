package dev.ae2lattice.server;

import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import dev.ae2lattice.mixin.CraftingCpuLogicAccessor;

/** Entry point for the mixin hooks. Server thread only. */
public final class LatticeTracker {
    private static final Map<CraftingCPUCluster, JobTrack> TRACKS = new WeakHashMap<>();

    @Nullable
    public static JobTrack get(CraftingCPUCluster cpu) {
        var job = ((CraftingCpuLogicAccessor) (Object) cpu.craftingLogic).ae2lattice$getJob();
        if (job == null) {
            TRACKS.remove(cpu);
            return null;
        }
        var jt = TRACKS.get(cpu);
        if (jt == null || jt.job.get() != job) {
            jt = new JobTrack(job);
            TRACKS.put(cpu, jt);
        }
        return jt;
    }

    public static void onPush(CraftingCPUCluster cpu, IPatternDetails details, ICraftingProvider provider) {
        var jt = get(cpu);
        if (jt != null) jt.onPush(details, provider);
    }

    public static void onReturn(CraftingCPUCluster cpu, AEKey what, long amount) {
        var jt = get(cpu);
        if (jt != null) jt.onReturn(what, amount);
    }

    /** What the last job on a CPU ended with, as reported by AE2 at the moment it finished. */
    public record Finished(long id, boolean success, GenericStack output, long remaining, long elapsedNanos,
            long tick, int steps, java.util.List<dev.ae2lattice.net.SnapshotData.UsedMachine> machines) {}

    private static final Map<CraftingCPUCluster, Finished> FINISHED = new WeakHashMap<>();

    public static void onFinish(CraftingCPUCluster cpu, boolean success, @Nullable GenericStack output,
            long remaining, long elapsedNanos) {
        if (output == null) return;
        int steps = 0;
        var machines = new java.util.LinkedHashMap<String, dev.ae2lattice.net.SnapshotData.UsedMachine>();
        var jt = TRACKS.get(cpu);
        if (jt != null) {
            steps = jt.patterns.size();
            for (var pt : jt.patterns.values()) for (var m : pt.machines.values()) {
                if (!m.used || machines.size() >= 12) continue;
                machines.putIfAbsent(m.dim.location() + "@" + m.pos.asLong(),
                        new dev.ae2lattice.net.SnapshotData.UsedMachine(m.icon, m.name, m.pos, m.dim.location()));
            }
        }
        FINISHED.put(cpu, new Finished(java.util.concurrent.ThreadLocalRandom.current().nextLong(), success, output,
                remaining, elapsedNanos, ServerHooks.tick(), steps, java.util.List.copyOf(machines.values())));
    }

    @Nullable
    static Finished lastFinished(CraftingCPUCluster cpu) {
        return FINISHED.get(cpu);
    }

    static void clear() {
        TRACKS.clear();
        FINISHED.clear();
    }

    private LatticeTracker() {}
}
