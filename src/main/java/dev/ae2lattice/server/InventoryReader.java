package dev.ae2lattice.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import dev.ae2lattice.net.SnapshotData.InvGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Reads what's in a machine's slots and tanks. READ-ONLY: only getStackInSlot / getFluidInTank, never extract.
 */
final class InventoryReader {
    private static final int MAX_ITEMS = 9, MAX_FLUIDS = 4;

    private static final class Builder {
        final String kind;
        final List<ItemStack> items = new ArrayList<>();
        int empty;
        final List<FluidStack> fluids = new ArrayList<>();
        final List<Integer> caps = new ArrayList<>();

        Builder(String kind) {
            this.kind = kind;
        }

        void item(ItemStack s) {
            if (s.isEmpty()) empty++;
            else if (items.size() < MAX_ITEMS) items.add(s.copy());
        }

        void fluid(FluidStack f, int cap) {
            if (!f.isEmpty() && fluids.size() < MAX_FLUIDS) {
                fluids.add(f.copy());
                caps.add(cap);
            }
        }

        void items(IItemHandler h) {
            for (int i = 0; i < h.getSlots(); i++) item(h.getStackInSlot(i));
        }

        void fluids(IFluidHandler h) {
            for (int i = 0; i < h.getTanks(); i++) fluid(h.getFluidInTank(i), h.getTankCapacity(i));
        }

        boolean isEmpty() {
            return items.isEmpty() && fluids.isEmpty() && empty == 0;
        }

        InvGroup build() {
            return new InvGroup(kind, items, Math.min(empty, MAX_ITEMS), fluids, caps);
        }
    }

    static List<InvGroup> read(@Nullable MinecraftServer server, ResourceKey<Level> dim, BlockPos pos) {
        if (server == null) return List.of();
        var level = server.getLevel(dim);
        if (level == null || !level.isLoaded(pos)) return List.of();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return List.of();
        try {
            if (be instanceof AbstractFurnaceBlockEntity f) {
                var in = new Builder("in");
                in.item(f.getItem(0));
                var fuel = new Builder("fuel");
                fuel.item(f.getItem(1));
                var out = new Builder("out");
                out.item(f.getItem(2));
                return List.of(in.build(), fuel.build(), out.build());
            }
            var gt = gregtech(be);
            if (gt != null) return gt;
            return generic(be);
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** GregTech: machine traits are tagged IN/OUT; for multiblocks, also look at the parts (buses/hatches). */
    @Nullable
    private static List<InvGroup> gregtech(BlockEntity be) {
        Object machine = Reflect.invoke(be, "getMetaMachine");
        if (machine == null) return null;
        Map<String, Builder> groups = new LinkedHashMap<>();
        groups.put("in", new Builder("in"));
        groups.put("out", new Builder("out"));
        collectTraits(machine, groups);
        if (Reflect.invoke(machine, "getParts") instanceof Collection<?> parts) {
            for (Object part : parts) collectTraits(part, groups);
        }
        var out = new ArrayList<InvGroup>();
        for (var b : groups.values()) if (!b.isEmpty()) out.add(b.build());
        return out;
    }

    private static void collectTraits(Object machine, Map<String, Builder> groups) {
        if (!(Reflect.invoke(machine, "getTraits") instanceof Collection<?> traits)) return;
        for (Object trait : traits) {
            Object io = Reflect.invoke(trait, "getHandlerIO");
            String kind = io instanceof Enum<?> e ? switch (e.name()) {
                case "IN", "BOTH" -> "in";
                case "OUT" -> "out";
                default -> null;
            } : null;
            if (kind == null) continue;
            var b = groups.get(kind);
            if (trait instanceof IItemHandler ih) b.items(ih);
            if (trait instanceof IFluidHandler fh) b.fluids(fh);
        }
    }

    /** Anything else: whatever its item/fluid capabilities expose (no in/out split known). */
    private static List<InvGroup> generic(BlockEntity be) {
        var b = new Builder("slots");
        be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(b::items);
        be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(b::fluids);
        return b.isEmpty() ? List.of() : List.of(b.build());
    }

    private InventoryReader() {}
}
