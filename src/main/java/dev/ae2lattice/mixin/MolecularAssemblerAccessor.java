package dev.ae2lattice.mixin;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only: what the assembler is crafting and what's in its grid. */
@Mixin(value = MolecularAssemblerBlockEntity.class, remap = false)
public interface MolecularAssemblerAccessor {
    @Accessor("myPlan")
    IMolecularAssemblerSupportedPattern lattice$getPlan();

    @Accessor("gridInv")
    AppEngInternalInventory lattice$getGrid();
}
