package dev.ae2lattice;

import net.minecraftforge.common.ForgeConfigSpec;

public final class LatticeConfig {
    public enum DefaultView { REMEMBER, GRID, TREE }

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.EnumValue<DefaultView> DEFAULT_VIEW;
    public static final ForgeConfigSpec.BooleanValue LAST_VIEW_TREE;
    public static final ForgeConfigSpec.BooleanValue ANIMATIONS;
    public static final ForgeConfigSpec.BooleanValue CELEBRATE;
    public static final ForgeConfigSpec.BooleanValue CELEBRATE_SOUND;
    public static final ForgeConfigSpec.IntValue HIGHLIGHT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue CLOSE_ON_HIGHLIGHT;

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.IntValue UPDATE_INTERVAL_TICKS;

    static {
        var c = new ForgeConfigSpec.Builder();
        c.push("view");
        DEFAULT_VIEW = c.comment("Which view the Crafting Status screen opens in.",
                        "REMEMBER = whatever you used last, GRID = always AE2's grid, TREE = always the Lattice tree.")
                .defineEnum("defaultView", DefaultView.REMEMBER);
        LAST_VIEW_TREE = c.comment("Last used view (managed automatically).")
                .define("lastViewWasTree", true);
        ANIMATIONS = c.comment("Show items travelling between steps.")
                .define("animations", true);
        CELEBRATE = c.comment("Short celebration (spinning item + confetti) when a job finishes while you watch.")
                .define("celebrate", true);
        CELEBRATE_SOUND = c.comment("Play a sound with the celebration.")
                .define("celebrateSound", true);
        c.pop();
        c.push("highlight");
        HIGHLIGHT_SECONDS = c.comment("How long a machine stays highlighted in the world after clicking it.")
                .defineInRange("seconds", 15, 1, 300);
        CLOSE_ON_HIGHLIGHT = c.comment("Close the screen when you click a machine so you can see the highlight.",
                        "Shift-click always keeps the screen open.")
                .define("closeScreen", true);
        c.pop();
        CLIENT_SPEC = c.build();

        var s = new ForgeConfigSpec.Builder();
        UPDATE_INTERVAL_TICKS = s.comment("How often (in ticks) open tree views are updated.")
                .defineInRange("updateIntervalTicks", 4, 1, 40);
        SERVER_SPEC = s.build();
    }

    public static boolean shouldOpenTree() {
        return switch (DEFAULT_VIEW.get()) {
            case GRID -> false;
            case TREE -> true;
            case REMEMBER -> LAST_VIEW_TREE.get();
        };
    }

    public static void rememberView(boolean tree) {
        if (LAST_VIEW_TREE.get() != tree) {
            LAST_VIEW_TREE.set(tree);
            LAST_VIEW_TREE.save();
        }
    }

    private LatticeConfig() {}
}
