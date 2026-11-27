package dev.ae2lattice.client.compat;

import dev.ae2lattice.client.TreeOverlay;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;

/**
 * Only loaded by EMI. While the Lattice tree covers the screen, claim the whole screen so EMI hides its panels
 * (and therefore can't receive clicks meant for the tree).
 */
@EmiEntrypoint
public class LatticeEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea((screen, consumer) -> {
            if (TreeOverlay.isCovering(screen)) {
                consumer.accept(new Bounds(0, 0, screen.width, screen.height));
            }
        });
    }
}
