package dev.ae2lattice.mixin;
import appeng.client.gui.AEBaseGui;
import dev.ae2lattice.client.ClientProxy;
import dev.ae2lattice.client.GraphRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=AEBaseGui.class,remap=false)
public abstract class ClientBaseGuiMixin {
    @Inject(method="handleMouseInput",at=@At("HEAD"),cancellable=true,remap=true)
    private void lattice$mouse(CallbackInfo ci){if(ClientProxy.active()){GraphRenderer.mouse();ci.cancel();}}
}
