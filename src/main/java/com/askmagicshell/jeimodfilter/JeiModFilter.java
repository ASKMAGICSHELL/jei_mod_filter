package com.askmagicshell.jeimodfilter;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(JeiModFilter.MOD_ID)
public final class JeiModFilter {
    public static final String MOD_ID = "jei_mod_filter";

    public JeiModFilter(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}