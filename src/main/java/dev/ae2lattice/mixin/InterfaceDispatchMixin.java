package dev.ae2lattice.mixin;
import appeng.helpers.DualityInterface;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import dev.ae2lattice.server.DispatchContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=DualityInterface.class,remap=false)
public abstract class InterfaceDispatchMixin {
    @Inject(method="onPushPatternSuccess",at=@At("HEAD"))
    private void lattice$target(TileEntity tile,ForgeDirection side,ICraftingPatternDetails pattern,CallbackInfo ci){
        try{DispatchContext.accepted(tile,pattern);}catch(RuntimeException ignored){}
    }
}
