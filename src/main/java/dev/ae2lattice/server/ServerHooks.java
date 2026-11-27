package dev.ae2lattice.server;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.ae2lattice.net.LatticeNetwork;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ServerHooks {
    private static final Logger LOG=LogManager.getLogger("AE2 Lattice");
    private static final Map<EntityPlayerMP,LatticeNetwork.Request> QUEUE=new ConcurrentHashMap<>();
    private final Map<EntityPlayerMP,Long> last=new WeakHashMap<>();
    private long tick;
    public static void enqueue(EntityPlayerMP player,LatticeNetwork.Request request) {
        if(QUEUE.size()<512) QUEUE.putIfAbsent(player,request);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        QUEUE.remove(event.player); last.remove(event.player);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        tick++;
        // Global cap; excess requests wait for the next server tick.
        int budget=4;
        for(Map.Entry<EntityPlayerMP,LatticeNetwork.Request> e:QUEUE.entrySet()){
            if(budget<=0) break;
            EntityPlayerMP p=e.getKey();
            LatticeNetwork.Request req=e.getValue();
            if(!QUEUE.remove(p,req)) continue;
            Long previous=last.get(p);
            if(previous!=null && tick-previous<10) continue;
            last.put(p,tick);
            if(p.isDead || !(p.openContainer instanceof ContainerCraftingCPU)
                || p.openContainer.windowId!=req.window || !p.openContainer.canInteractWith(p)) continue;
            ContainerCraftingCPU container=(ContainerCraftingCPU)p.openContainer;
            CraftingCPUCluster cpu=container.getCpu();
            if(cpu==null) continue;
            IGrid grid=cpu.getGrid();
            if(grid==null) continue;
            ISecurityGrid security=grid.getCache(ISecurityGrid.class);
            if(security==null || !security.hasPermission(p,SecurityPermissions.CRAFT)) continue;
            budget--;
            try {
                LatticeNetwork.channel.sendTo(new LatticeNetwork.Response(
                    SnapshotBuilder.build(cpu,p,req.window,req.nonce)),p);
            } catch(Exception ex) { LOG.debug("Could not build crafting snapshot",ex); }
        }
    }
}
