package dev.ae2lattice.client;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

public final class ClientInit {
    public static void init() {
        MinecraftForge.EVENT_BUS.register(TreeOverlay.class);
        MinecraftForge.EVENT_BUS.register(Highlighter.class);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientState.reset());
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent e) ->
                        e.register(IconLine.class, IconLine.Client::new));
    }

    private ClientInit() {}
}
