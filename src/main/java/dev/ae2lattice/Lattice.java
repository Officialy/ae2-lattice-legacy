package dev.ae2lattice;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import dev.ae2lattice.server.JobTrack;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import dev.ae2lattice.net.LatticeNetwork;
import dev.ae2lattice.server.ServerHooks;
import java.util.Map;

@Mod(modid = Lattice.ID, name = "AE2 Lattice Legacy", version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*",
    dependencies = "required-after:appliedenergistics2;after:gregtech")
public final class Lattice {
    public static final String ID = "ae2lattice";
    @SidedProxy(clientSide = "dev.ae2lattice.client.ClientProxy", serverSide = "dev.ae2lattice.CommonProxy")
    public static CommonProxy proxy;

    // Optional on BOTH sides; the application protocol is checked independently.
    @NetworkCheckHandler
    public boolean checkRemote(Map<String, String> remote, Side side) {
        return true;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LatticeNetwork.init();
        FMLCommonHandler.instance().bus().register(new ServerHooks());
        proxy.init();
    }
    @Mod.EventHandler public void serverStopped(FMLServerStoppedEvent event) {
        ServerHooks.clearQueue();
        JobTrack.clearAll();
    }
}
