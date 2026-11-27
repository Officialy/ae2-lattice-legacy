package dev.ae2lattice.server;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.util.NamedDimensionalCoord;
import net.minecraft.tileentity.TileEntity;
import java.util.*;

/** Stack-scoped observations: never retains a live tile or attributes another CPU's calls. */
public final class DispatchContext implements AutoCloseable {
    private static final ThreadLocal<DispatchContext> CURRENT = new ThreadLocal<>();
    private final DispatchContext previous;
    private final ICraftingPatternDetails pattern;
    private final Set<NamedDimensionalCoord> destinations = new LinkedHashSet<>();
    private boolean partial;
    private DispatchContext(ICraftingPatternDetails pattern) {
        this.pattern=pattern;previous=CURRENT.get();CURRENT.set(this);
    }
    public static DispatchContext begin(ICraftingPatternDetails pattern){return new DispatchContext(pattern);}
    public static void accepted(TileEntity tile,ICraftingPatternDetails pattern){
        DispatchContext context=CURRENT.get();
        if(context==null || context.pattern!=pattern || tile==null || tile.getWorldObj()==null)return;
        if(context.destinations.size()>=8){context.partial=true;return;}
        context.destinations.add(new NamedDimensionalCoord(tile,tile.getClass().getSimpleName()));
    }
    List<NamedDimensionalCoord> destinations(){return new ArrayList<>(destinations);}
    boolean partial(){return partial;}
    @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
