package com.askmagicshell.jeimodfilter;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PANEL_WIDTH =
        BUILDER.comment("Width of the JEI mod filter panel in GUI pixels.")
            .defineInRange("panelWidth", 148, 120, 300);

    public static final ModConfigSpec.BooleanValue PANEL_HIDDEN =
        BUILDER.comment("Whether the JEI mod filter panel starts hidden.")
            .define("panelHidden", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    public static int panelWidth() {
        return PANEL_WIDTH.get();
    }

    public static boolean panelHidden() {
        return PANEL_HIDDEN.get();
    }

    public static void setPanelWidth(int width) {
        PANEL_WIDTH.set(Math.clamp(width, 120, 300));
        SPEC.save();
    }

    public static void setPanelHidden(boolean hidden) {
        PANEL_HIDDEN.set(hidden);
        SPEC.save();
    }
}
