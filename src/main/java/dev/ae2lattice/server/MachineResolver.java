package dev.ae2lattice.server;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternProviderLogic;
import dev.ae2lattice.mixin.PatternProviderLogicAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * Figures out which block in the world a crafting provider is actually feeding.
 * Read-only: only looks at block states, block entities and capabilities.
 */
public final class MachineResolver {

    public record Target(BlockPos pos, ResourceKey<Level> dim, ItemStack icon, Component name) {}

    public static List<Target> resolve(ICraftingProvider provider) {
        var out = new ArrayList<Target>(2);
        try {
            if (provider instanceof PatternProviderLogic ppl) {
                var host = ((PatternProviderLogicAccessor) (Object) ppl).ae2lattice$getHost();
                BlockEntity be = host.getBlockEntity();
                if (be == null || be.getLevel() == null) return out;
                Level level = be.getLevel();
                BlockPos pos = be.getBlockPos();
                for (Direction dir : host.getTargets()) {
                    BlockPos adj = pos.relative(dir);
                    if (!level.isLoaded(adj)) continue;
                    BlockEntity abe = level.getBlockEntity(adj);
                    if (abe == null) continue;
                    var side = dir.getOpposite();
                    // provider -> ME interface of a subnet -> storage bus(es) -> the real machine
                    var viaSubnet = throughSubnet(level, abe, side, ppl.getGrid());
                    if (!viaSubnet.isEmpty()) {
                        out.addAll(viaSubnet);
                        continue;
                    }
                    boolean accepts = abe.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent()
                            || abe.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent();
                    if (!accepts) continue;
                    out.add(describe(level, followMultiblock(abe).orElse(adj)));
                }
                if (out.isEmpty()) out.add(describe(level, pos)); // fall back to the provider itself
            } else {
                // Non-AE2 providers, e.g. GregTech's ME Pattern Buffer (a multiblock part).
                var lvl = invoke(provider, "getLevel");
                var p = invoke(provider, "getPos");
                if (lvl instanceof Level level && p instanceof BlockPos pos) {
                    var ctrl = controllerOf(provider);
                    out.add(describe(level, ctrl != null ? ctrl : pos));
                }
            }
        } catch (Throwable ignored) {
            // never let observation break crafting
        }
        return out;
    }

    /**
     * Subnet setups: the provider feeds an ME interface on a separate network, whose storage buses face the actual
     * machine (e.g. an EBF's input bus + fluid hatch). Follow that, read-only, to the blocks those buses face.
     */
    private static List<Target> throughSubnet(Level level, BlockEntity adjacent, Direction faceTowardProvider,
            @Nullable appeng.api.networking.IGrid providerGrid) {
        Object candidate = adjacent;
        if (adjacent instanceof appeng.api.parts.IPartHost ph) {
            var part = ph.getPart(faceTowardProvider);
            if (part != null) candidate = part;
        }
        if (!(candidate instanceof appeng.helpers.InterfaceLogicHost)
                || !(candidate instanceof appeng.api.networking.security.IActionHost ah)) return List.of();
        var node = ah.getActionableNode();
        var grid = node == null ? null : node.getGrid();
        if (grid == null || grid == providerGrid) return List.of(); // same network: not a subnet setup
        Component ifaceName = customName(candidate); // e.g. renamed in an anvil: "Kanthal EBF"
        var res = new java.util.LinkedHashMap<BlockPos, Target>();
        for (var cls : grid.getMachineClasses()) {
            if (!appeng.parts.storagebus.StorageBusPart.class.isAssignableFrom(cls)) continue;
            for (var n : grid.getMachineNodes(cls)) {
                if (!(n.getOwner() instanceof appeng.parts.storagebus.StorageBusPart bus)) continue;
                var busBe = bus.getBlockEntity();
                if (busBe == null || bus.getSide() == null) continue;
                BlockPos tpos = busBe.getBlockPos().relative(bus.getSide());
                if (!level.isLoaded(tpos)) continue;
                var tbe = level.getBlockEntity(tpos);
                BlockPos fin = tbe != null ? followMultiblock(tbe).orElse(tpos) : tpos;
                res.computeIfAbsent(fin.immutable(), p -> {
                    var t = describe(level, p);
                    return ifaceName != null ? new Target(t.pos(), t.dim(), t.icon(), ifaceName) : t;
                });
                if (res.size() >= 8) break;
            }
        }
        return new ArrayList<>(res.values());
    }

    private static Target describe(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        ItemStack icon;
        try {
            icon = state.getBlock().getCloneItemStack(level, pos, state);
        } catch (Throwable t) {
            icon = ItemStack.EMPTY;
        }
        if (icon.isEmpty()) icon = new ItemStack(state.getBlock());
        Component name = icon.isEmpty() ? state.getBlock().getName() : icon.getHoverName();
        // a custom (anvil) name on the block wins
        var custom = customName(level.getBlockEntity(pos));
        if (custom != null) name = custom;
        return new Target(pos.immutable(), level.dimension(), icon.copyWithCount(1), name);
    }

    /** Custom name given in an anvil (blocks, AE2 parts, GregTech machines), if any. */
    @Nullable
    private static Component customName(@Nullable Object o) {
        if (o == null) return null;
        if (o instanceof net.minecraft.world.Nameable n && n.hasCustomName()) return n.getCustomName();
        // AE2 parts / GT machines expose it without implementing Nameable
        if (invoke(o, "hasCustomName") instanceof Boolean b && b && invoke(o, "getCustomName") instanceof Component c) return c;
        var mm = invoke(o, "getMetaMachine");
        if (mm != null && invoke(mm, "hasCustomName") instanceof Boolean b2 && b2
                && invoke(mm, "getCustomName") instanceof Component c2) return c2;
        return null;
    }

    /** GregTech: if this block is a multiblock part, return the controller's position. */
    private static Optional<BlockPos> followMultiblock(BlockEntity be) {
        var mm = invoke(be, "getMetaMachine");
        if (mm == null) return Optional.empty();
        return Optional.ofNullable(controllerOf(mm));
    }

    @Nullable
    private static BlockPos controllerOf(Object machine) {
        var ctrls = invoke(machine, "getControllers");
        if (ctrls instanceof Collection<?> c && !c.isEmpty()) {
            Object ctrl = c.iterator().next();
            Object self = invoke(ctrl, "self");
            Object p = invoke(self != null ? self : ctrl, "getPos");
            if (p instanceof BlockPos bp) return bp;
        }
        return null;
    }

    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    @Nullable
    private static Object invoke(@Nullable Object target, String name) {
        if (target == null) return null;
        var cls = target.getClass();
        var m = METHODS.computeIfAbsent(cls.getName() + "#" + name, k -> {
            try {
                return Optional.of(cls.getMethod(name));
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
        if (m.isEmpty()) return null;
        try {
            return m.get().invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private MachineResolver() {}
}
