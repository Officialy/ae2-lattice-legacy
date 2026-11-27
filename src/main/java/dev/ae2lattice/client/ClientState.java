package dev.ae2lattice.client;

import dev.ae2lattice.Lattice;
import dev.ae2lattice.net.LatticeNetwork;
import dev.ae2lattice.net.SnapshotData;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

public final class ClientState {
    public static final TreeModel MODEL = new TreeModel();
    private static boolean subscribed;

    /** For the empty-state message: what do we know about why there's nothing to show? */
    static long subscribedAtMs;
    static boolean receivedSinceSubscribe;
    static byte lastEmptyReason;
    /** How the last job on the viewed CPU ended (from AE2), if the server told us. */
    @org.jetbrains.annotations.Nullable
    static SnapshotData.Finished lastFinished;
    private static String lastLogged = "";

    public static void accept(SnapshotData data) {
        long now = Util.getMillis();
        receivedSinceSubscribe = true;
        lastEmptyReason = data.hasJob ? 0 : data.emptyReason;
        lastFinished = data.hasJob ? null : data.finished;
        boolean refit = MODEL.apply(data, now);
        log(data.hasJob ? "receiving job data (" + MODEL.patterns.size() + " patterns, " + MODEL.nodes.size() + " nodes)"
                : "server reports " + (data.emptyReason == SnapshotData.EMPTY_NO_CPU ? "no CPU selected" : "no job on this CPU"));
        TreeOverlay.onSnapshot(data, refit);
    }

    public static boolean serverHasLattice() {
        var conn = Minecraft.getInstance().getConnection();
        return conn != null && LatticeNetwork.CHANNEL.isRemotePresent(conn.getConnection());
    }

    public static void setSubscribed(boolean on) {
        if (!serverHasLattice()) {
            subscribed = false;
            log("server doesn't have Lattice");
            return;
        }
        if (on && !subscribed) {
            subscribedAtMs = Util.getMillis();
            receivedSinceSubscribe = false;
            log("subscribing");
        }
        if (on || subscribed) {
            LatticeNetwork.CHANNEL.sendToServer(new LatticeNetwork.SubscribePacket(on));
        }
        subscribed = on;
        if (!on) {
            MODEL.clear();
            lastLogged = "";
        }
    }

    /** Start a fresh subscription so the server sends the complete tree again. */
    public static void resubscribe() {
        if (!subscribed || !serverHasLattice()) return;
        receivedSinceSubscribe = false;
        subscribedAtMs = Util.getMillis();
        LatticeNetwork.CHANNEL.sendToServer(new LatticeNetwork.SubscribePacket(true));
    }

    private static void log(String state) {
        if (!state.equals(lastLogged)) {
            lastLogged = state;
            Lattice.LOG.debug("Lattice (client): {}", state);
        }
    }

    public static void reset() {
        subscribed = false;
        receivedSinceSubscribe = false;
        MODEL.clear();
    }

    private ClientState() {}
}
