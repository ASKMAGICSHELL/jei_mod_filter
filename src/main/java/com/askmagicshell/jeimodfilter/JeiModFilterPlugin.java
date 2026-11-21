package com.askmagicshell.jeimodfilter;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
@JeiPlugin
public final class JeiModFilterPlugin implements IModPlugin {
    private static volatile IJeiRuntime runtime;
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(JeiModFilter.MOD_ID, "plugin");
    }
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }
    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
    public static IJeiRuntime getRuntime() {
        return runtime;
    }
}
