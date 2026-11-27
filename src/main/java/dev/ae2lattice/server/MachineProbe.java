package dev.ae2lattice.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.ae2lattice.net.SnapshotData.InvGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import dev.ae2lattice.mixin.AbstractFurnaceAccessor;
import dev.ae2lattice.net.SnapshotData.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Reads a machine's own state. Only reports what the machine itself knows - no estimates.
 * Read-only: never changes anything.
 */
final class MachineProbe {

    /**
     * @param making    items the machine's current recipe produces (GregTech), empty if unknown
     * @param consuming item currently being processed (furnace input slot), null if unknown
     */
    /** @param recipe the running recipe as two groups ("in", "out"); empty if the machine doesn't expose it */
    /** @param recipeId ID of the recipe the machine is running right now (for showing it in EMI), if known */
    record Reading(MachineState state, int progress, int maxProgress, @Nullable Component reason,
            Set<Item> making, @Nullable Item consuming, List<InvGroup> recipe, @Nullable ResourceLocation recipeId) {
        static final Reading UNLOADED = new Reading(MachineState.UNLOADED, 0, 0, null, Set.of(), null, List.of(), null);
        static final Reading UNKNOWN = new Reading(MachineState.UNKNOWN, 0, 0, null, Set.of(), null, List.of(), null);

        static Reading of(MachineState s, int progress, int max, @Nullable Component reason) {
            return new Reading(s, progress, max, reason, Set.of(), null, List.of(), null);
        }
    }

    private static final Set<String> UNREADABLE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static Reading probe(@Nullable MinecraftServer server, ResourceKey<Level> dim, BlockPos pos) {
        if (server == null) return Reading.UNKNOWN;
        var level = server.getLevel(dim);
        if (level == null || !level.isLoaded(pos)) return Reading.UNLOADED;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return Reading.UNKNOWN;
        try {
            if (be instanceof AbstractFurnaceBlockEntity furnace) return furnace(furnace);
            if (be instanceof appeng.blockentity.crafting.MolecularAssemblerBlockEntity ma) return assembler(ma);
            var gt = gregtech(be);
            if (gt != null) return gt;
        } catch (Throwable ignored) {
            // an unexpected machine implementation must never break the snapshot
        }
        return Reading.UNKNOWN;
    }

    private static Reading furnace(AbstractFurnaceBlockEntity f) {
        var acc = (AbstractFurnaceAccessor) f;
        int lit = acc.ae2lattice$getLitTime();
        int progress = acc.ae2lattice$getCookingProgress();
        int total = acc.ae2lattice$getCookingTotalTime();
        var input = f.getItem(0);
        var fuel = f.getItem(1);
        var output = f.getItem(2);
        Item consuming = input.isEmpty() ? null : input.getItem();
        MachineState st;
        if (lit > 0 && progress > 0) st = MachineState.WORKING;
        else if (input.isEmpty()) st = MachineState.IDLE;
        else if (!output.isEmpty() && output.getCount() >= output.getMaxStackSize()) st = MachineState.OUTPUT_FULL;
        else if (lit <= 0 && fuel.isEmpty()) st = MachineState.NO_FUEL;
        else st = MachineState.WORKING; // has input and fuel, about to advance
        if (st == MachineState.IDLE) return Reading.of(st, 0, 0, null);
        // which smelting recipe is it on? (vanilla recipe lookup for the item in its input slot)
        ResourceLocation rid = null;
        if (f.getLevel() != null) {
            RecipeType<? extends AbstractCookingRecipe> type = f instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING
                    : f instanceof SmokerBlockEntity ? RecipeType.SMOKING : RecipeType.SMELTING;
            rid = f.getLevel().getRecipeManager().getRecipeFor(type, f, f.getLevel()).map(r -> r.getId()).orElse(null);
        }
        return new Reading(st, progress, total, null, Set.of(), consuming, List.of(), rid);
    }

    /** AE2 molecular assembler: progress 0-100 %, powered, and the pattern it's working on. */
    private static Reading assembler(appeng.blockentity.crafting.MolecularAssemblerBlockEntity ma) {
        var acc = (dev.ae2lattice.mixin.MolecularAssemblerAccessor) ma;
        var plan = acc.lattice$getPlan();
        boolean gridEmpty = acc.lattice$getGrid().isEmpty();
        if (plan == null && gridEmpty) return Reading.of(MachineState.IDLE, 0, 0, null);
        if (!ma.isPowered()) {
            return new Reading(MachineState.WAITING, 0, 0, Component.literal("no power"), Set.of(), null, List.of(), null);
        }
        Set<Item> making = new LinkedHashSet<>();
        var out = plan == null ? null : plan.getPrimaryOutput();
        if (out != null && out.what() instanceof appeng.api.stacks.AEItemKey ik) making.add(ik.getItem());
        // progress is a percentage for assemblers: encoded as maxProgress = -100 (see MachineInfo.isPercent)
        return new Reading(MachineState.WORKING, ma.getCraftingProgress(), -100, null, making, null, List.of(), null);
    }

    /** GregTech CEu: MetaMachineBlockEntity -> MetaMachine -> RecipeLogic. Reflection, so GT stays optional. */
    @Nullable
    private static Reading gregtech(BlockEntity be) {
        Object machine = Reflect.invoke(be, "getMetaMachine");
        if (machine == null) return null;
        Object logic = Reflect.invoke(machine, "getRecipeLogic");
        if (logic == null) return null;
        Object status = Reflect.invoke(logic, "getStatus");
        int progress = Reflect.invoke(logic, "getProgress") instanceof Integer i ? i : 0;
        int max = Reflect.invoke(logic, "getMaxProgress") instanceof Integer i ? i : 0;
        Component reason = Reflect.invoke(logic, "getWaitingReason") instanceof Component c ? c : null;
        String s = status instanceof Enum<?> e ? e.name() : "";
        MachineState st = switch (s) {
            case "WORKING" -> MachineState.WORKING;
            case "WAITING" -> MachineState.WAITING;
            case "SUSPEND" -> MachineState.SUSPENDED;
            case "IDLE" -> MachineState.IDLE;
            default -> MachineState.UNKNOWN;
        };
        if (st == MachineState.IDLE) return Reading.of(st, 0, 0, null);
        // Which recipe is it running? (what Jade shows as "Recipe Outputs")
        Object recipe = Reflect.invoke(logic, "getLastRecipe");
        Set<Item> making = recipeOutputItems(recipe);
        if (st == MachineState.WORKING && making.isEmpty()) {
            // Without this we can't tell which step a shared machine is working on - log once per machine type.
            if (UNREADABLE.add(machine.getClass().getName())) {
                dev.ae2lattice.Lattice.LOG.info("Lattice: can't read the current recipe of {} (recipe={}) - "
                        + "steps sharing this machine may both show as working", machine.getClass().getSimpleName(),
                        recipe == null ? "null" : recipe.getClass().getSimpleName());
            }
        }
        ResourceLocation rid = recipe instanceof Recipe<?> r ? r.getId() : null;
        return new Reading(st, progress, max, st == MachineState.WAITING ? reason : null, making, null,
                recipeGroups(recipe), rid);
    }

    /**
     * The running recipe's inputs and outputs with amounts (what Jade shows). GregTech consumes inputs when a
     * recipe starts, so during processing they're in no slot - this is where they are.
     */
    private static List<InvGroup> recipeGroups(@Nullable Object recipe) {
        if (recipe == null) return List.of();
        var in = contents(Reflect.field(recipe, "inputs"), "in", true);
        var out = contents(Reflect.field(recipe, "outputs"), "out", false);
        var res = new ArrayList<InvGroup>(2);
        if (!in.items().isEmpty() || !in.fluids().isEmpty()) res.add(in);
        if (!out.items().isEmpty() || !out.fluids().isEmpty()) res.add(out);
        return res;
    }

    private static InvGroup contents(@Nullable Object map, String kind, boolean skipNotConsumed) {
        var items = new ArrayList<ItemStack>();
        var fluids = new ArrayList<FluidStack>();
        var caps = new ArrayList<Integer>();
        if (map instanceof Map<?, ?> m) {
            for (var list : m.values()) {
                if (!(list instanceof Collection<?> cs)) continue;
                for (var content : cs) {
                    // GT marks non-consumed inputs (e.g. programmed circuits) with chance 0
                    if (skipNotConsumed && Reflect.field(content, "chance") instanceof Integer ch && ch == 0) continue;
                    Object c = Reflect.field(content, "content");
                    if (c instanceof Ingredient ing && items.size() < 9) {
                        var stacks = ing.getItems();
                        if (stacks.length == 0) continue;
                        var s = stacks[0].copy();
                        if (Reflect.invoke(ing, "getAmount") instanceof Integer amt) s.setCount(Math.max(1, amt));
                        items.add(s);
                    } else if (c != null && fluids.size() < 4
                            && Reflect.invoke(c, "getStacks") instanceof FluidStack[] fs && fs.length > 0) {
                        var f = fs[0].copy();
                        if (Reflect.invoke(c, "getAmount") instanceof Integer amt) f.setAmount(amt);
                        fluids.add(f);
                        caps.add(0); // recipe amount, not a tank - drawn as full
                    }
                }
            }
        }
        return new InvGroup(kind, items, 0, fluids, caps);
    }

    private static Set<Item> recipeOutputItems(@Nullable Object recipe) {
        var out = new LinkedHashSet<Item>();
        if (recipe == null) return out;
        if (!(Reflect.field(recipe, "outputs") instanceof Map<?, ?> outputs)) return out;
        for (var list : outputs.values()) {
            if (!(list instanceof Collection<?> contents)) continue;
            for (var content : contents) {
                if (Reflect.field(content, "content") instanceof Ingredient ing) {
                    for (var stack : ing.getItems()) if (!stack.isEmpty()) out.add(stack.getItem());
                }
            }
        }
        return out;
    }

    private MachineProbe() {}
}
