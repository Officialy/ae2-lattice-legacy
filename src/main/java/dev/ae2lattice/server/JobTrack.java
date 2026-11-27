package dev.ae2lattice.server;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.ExecutingCraftingJob;
import dev.ae2lattice.net.SnapshotData.Event;

/** Lattice's record of one running job on one CPU. */
public final class JobTrack {
    final WeakReference<ExecutingCraftingJob> job;
    public final long uid = ThreadLocalRandom.current().nextLong();
    final Map<IPatternDetails, PatternTrack> patterns = new LinkedHashMap<>();
    private int nextId;
    int structureVersion;

    private record SeqEvent(long seq, long tick, Event event) {}

    private final ArrayDeque<SeqEvent> events = new ArrayDeque<>();
    private long lastSeq;

    JobTrack(ExecutingCraftingJob job) {
        this.job = new WeakReference<>(job);
    }

    PatternTrack track(IPatternDetails d) {
        var pt = patterns.get(d);
        if (pt == null) {
            pt = new PatternTrack(nextId++, d, ServerHooks.tick());
            patterns.put(d, pt);
            structureVersion++;
        }
        return pt;
    }

    void onPush(IPatternDetails d, ICraftingProvider provider) {
        long now = ServerHooks.tick();
        var pt = track(d);
        pt.pushed(now);
        for (var t : MachineResolver.resolve(provider)) {
            pt.addMachine(t, provider, true);
        }
        addEvent(new Event(pt.id, true, null, 1), now);
    }

    void onReturn(AEKey what, long amount) {
        long now = ServerHooks.tick();
        long left = amount;
        int guard = 0;
        while (left > 0 && guard++ < 10_000) {
            PatternTrack best = null;
            PatternTrack.InFlight bestF = null;
            long bestNeed = 0;
            for (var pt : patterns.values()) {
                for (var f : pt.inflight) {
                    long need = f.remaining.getOrDefault(what, 0L);
                    if (need > 0) {
                        if (bestF == null || f.tick < bestF.tick) {
                            best = pt;
                            bestF = f;
                            bestNeed = need;
                        }
                        break; // oldest in-flight of this pattern that needs it
                    }
                }
            }
            if (best == null) {
                // Nothing we saw being pushed (e.g. the job was running before a restart):
                // attribute it to whichever pattern produces this item.
                PatternTrack producer = null;
                for (var pt : patterns.values()) {
                    if (pt.produces(what)) {
                        producer = pt;
                        break;
                    }
                }
                if (producer != null) producer.returned(now);
                addEvent(new Event(producer != null ? producer.id : -1, false, what, left), now);
                return;
            }
            long take = Math.min(left, bestNeed);
            bestF.remaining.put(what, bestNeed - take);
            left -= take;
            if (bestF.done()) best.inflight.remove(bestF);
            best.returned(now);
            addEvent(new Event(best.id, false, what, take), now);
        }
    }

    private void addEvent(Event e, long now) {
        var last = events.peekLast();
        if (last != null && last.tick == now && last.event.patternId() == e.patternId()
                && last.event.push() == e.push() && java.util.Objects.equals(last.event.key(), e.key())) {
            events.pollLast();
            e = new Event(e.patternId(), e.push(), e.key(), last.event.amount() + e.amount());
            events.addLast(new SeqEvent(last.seq, now, e));
            return;
        }
        events.addLast(new SeqEvent(++lastSeq, now, e));
        while (events.size() > 1024 || (!events.isEmpty() && now - events.peekFirst().tick > 200)) {
            events.pollFirst();
        }
    }

    long lastSeq() {
        return lastSeq;
    }

    List<Event> eventsSince(long seq) {
        var out = new ArrayList<Event>();
        for (var e : events) if (e.seq > seq) out.add(e.event);
        return out;
    }
}
