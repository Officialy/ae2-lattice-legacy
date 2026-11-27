package dev.ae2lattice;

import com.mojang.logging.LogUtils;
import dev.ae2lattice.net.LatticeNetwork;
import dev.ae2lattice.server.ServerHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Lattice.MOD_ID)
public class Lattice {
    public static final String MOD_ID = "ae2lattice";
    public static final Logger LOG = LogUtils.getLogger();

    public Lattice() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LatticeConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LatticeConfig.SERVER_SPEC);
        LatticeNetwork.register();
        MinecraftForge.EVENT_BUS.register(ServerHooks.class);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> dev.ae2lattice.client.ClientInit::init);
    }

    /** Load the hooked AE2 classes now, so an incompatible AE2 version fails loudly at startup, not mid-craft. */
    private void commonSetup(FMLCommonSetupEvent e) {
        String[] hooked = {
                "appeng.crafting.execution.CraftingCpuLogic",
                "appeng.crafting.execution.ExecutingCraftingJob",
                "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress",
                "appeng.menu.me.crafting.CraftingCPUMenu",
                "appeng.helpers.patternprovider.PatternProviderLogic",
                "appeng.me.service.CraftingService",
        };
        int ok = 0;
        for (var name : hooked) {
            try {
                Class.forName(name, true, Lattice.class.getClassLoader());
                ok++;
            } catch (Throwable t) {
                LOG.error("Lattice: could not hook {} - the tree view will not work with this AE2 version", name, t);
            }
        }
        LOG.info("Lattice: {}/{} AE2 hooks applied", ok, hooked.length);
    }
}
