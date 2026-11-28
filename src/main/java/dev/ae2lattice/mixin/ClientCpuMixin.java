package dev.ae2lattice.mixin;
import appeng.client.gui.implementations.GuiCraftingCPU;
import dev.ae2lattice.client.ClientProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=GuiCraftingCPU.class,remap=false)
public abstract class ClientCpuMixin {
    @Inject(method="keyTyped",at=@At("HEAD"),cancellable=true,remap=true)
    private void lattice$key(char c,int key,CallbackInfo ci){if(ClientProxy.key(c,key))ci.cancel();}
}
