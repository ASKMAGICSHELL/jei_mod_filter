package com.askmagicshell.jeimodfilter.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.askmagicshell.jeimodfilter.JeiModFilterPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = "jei_mod_filter", value = Dist.CLIENT)
public final class RecipeGuiEvents {
    private static final String RECIPE_GUI_CLASS = "mezz.jei.gui.recipes.RecipesGui";
    private static final String CREATIVE_GUI_CLASS = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen";
    private static final int PANEL_WIDTH = 148;
    private static final int PANEL_MAX_HEIGHT = 248;
    private static final int HEADER_HEIGHT = 25;
    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_MARGIN = 6;
    private static final Map<String, ResourceLocation> ICONS = new HashMap<>();
    private static final Map<String, Boolean> ICON_LOOKUPS = new HashMap<>();
    private static List<ModEntry> mods;
    private static PanelLayout lastLayout;
    private static int scrollOffset;

    private RecipeGuiEvents() {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isSupportedGui(screen)) {
            lastLayout = null;
            return;
        }
        IJeiRuntime runtime = JeiModFilterPlugin.getRuntime();
        if (runtime == null) return;
        List<ModEntry> entries = getMods();
        PanelLayout layout = createLayout(screen);
        lastLayout = layout;
        clampScroll(entries, layout);
        drawPanel(event.getGuiGraphics(), runtime, entries, layout, event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !isSupportedGui(event.getScreen()) || lastLayout == null) return;
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        PanelLayout layout = lastLayout;
        if (!layout.contains(mouseX, mouseY) && !layout.clearContains(mouseX, mouseY)) return;
        IJeiRuntime runtime = JeiModFilterPlugin.getRuntime();
        if (runtime == null) return;
        if (layout.clearContains(mouseX, mouseY)) {
            runtime.getIngredientFilter().setFilterText("");
            event.setCanceled(true);
            return;
        }
        if (layout.listContains(mouseX, mouseY)) {
            int row = (int) ((mouseY - layout.listTop()) / ROW_HEIGHT);
            int index = scrollOffset + row;
            List<ModEntry> entries = getMods();
            if (index >= 0 && index < entries.size()) {
                runtime.getIngredientFilter().setFilterText("@" + entries.get(index).modId());
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!isSupportedGui(event.getScreen()) || lastLayout == null ||
            !lastLayout.listContains(event.getMouseX(), event.getMouseY())) return;
        int max = Math.max(0, getMods().size() - lastLayout.visibleRows());
        scrollOffset = Math.clamp(scrollOffset + (event.getScrollDeltaY() > 0 ? -1 : 1), 0, max);
        event.setCanceled(true);
    }

    private static boolean isRecipeGui(Screen screen) {
        return screen.getClass().getName().equals(RECIPE_GUI_CLASS);
    }

    private static boolean isCreativeGui(Screen screen) {
        return screen.getClass().getName().equals(CREATIVE_GUI_CLASS);
    }

    private static boolean isSupportedGui(Screen screen) {
        return isRecipeGui(screen) || isCreativeGui(screen);
    }

    private static List<ModEntry> getMods() {
        if (mods == null) {
            mods = ModList.get().getMods().stream()
                .map(info -> new ModEntry(info.getModId(), info.getDisplayName(), info))
                .sorted(Comparator.comparing(ModEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ModEntry::modId))
                .toList();
        }
        return mods;
    }

    private static PanelLayout createLayout(Screen screen) {
        int guiX = (screen.width - 176) / 2;
        int guiY = 48;
        int guiWidth = 176;
        int guiHeight = Math.max(120, screen.height - 96);

        if (isCreativeGui(screen)) {
            try {
                guiX = readFieldInt(screen, "leftPos");
                guiY = readFieldInt(screen, "topPos");
                guiWidth = readFieldInt(screen, "imageWidth");
                guiHeight = readFieldInt(screen, "imageHeight");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Keep a centered fallback if Minecraft changes its container screen fields.
            }
        } else {
            try {
                Object properties = screen.getClass().getMethod("getProperties").invoke(screen);
                if (properties != null) {
                    guiX = readInt(properties, "guiLeft");
                    guiY = readInt(properties, "guiTop");
                    guiWidth = readInt(properties, "guiXSize");
                    guiHeight = readInt(properties, "guiYSize");
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to JEI's usual centered recipe-view dimensions.
            }
        }

        int height = Math.min(PANEL_MAX_HEIGHT, Math.max(120, guiHeight));
        int x = guiX - PANEL_WIDTH - PANEL_MARGIN;
        if (x < 4) x = guiX + guiWidth + PANEL_MARGIN;
        if (x + PANEL_WIDTH > screen.width - 4) x = screen.width - PANEL_WIDTH - 4;
        int y = Math.max(30, Math.min(guiY, screen.height - height - 8));
        int[] clearButton = isRecipeGui(screen)
            ? findSearchClearButton(screen, x, y)
            : new int[]{x + PANEL_WIDTH - 20, y + 6};
        return new PanelLayout(x, y, PANEL_WIDTH, height, clearButton[0], clearButton[1]);
    }

    private static int readFieldInt(Object target, String fieldName) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static int[] findSearchClearButton(Screen screen, int fallbackX, int fallbackY) {
        try {
            Field overlayField = screen.getClass().getDeclaredField("ingredientListOverlay");
            overlayField.setAccessible(true);
            Object overlay = overlayField.get(screen);
            Field searchField = overlay.getClass().getDeclaredField("searchField");
            searchField.setAccessible(true);
            Object textField = searchField.get(overlay);
            int searchX = readInt(textField, "getX");
            int searchY = readInt(textField, "getY");
            int searchWidth = readInt(textField, "getWidth");
            int searchHeight = readInt(textField, "getHeight");
            return new int[]{searchX + searchWidth - 14, searchY + (searchHeight - 12) / 2};
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep a usable clear control in the panel header if JEI changes its search widget internals.
            return new int[]{fallbackX + PANEL_WIDTH - 20, fallbackY + 6};
        }
    }

    private static int readInt(Object target, String methodName) throws ReflectiveOperationException {
        return ((Number) target.getClass().getMethod(methodName).invoke(target)).intValue();
    }

    private static void clampScroll(List<ModEntry> entries, PanelLayout layout) {
        int max = Math.max(0, entries.size() - layout.visibleRows());
        scrollOffset = Math.clamp(scrollOffset, 0, max);
    }

    private static void drawPanel(
        GuiGraphics graphics, IJeiRuntime runtime, List<ModEntry> entries,
        PanelLayout layout, int mouseX, int mouseY
    ) {
        int x = layout.x();
        int y = layout.y();
        int right = x + layout.width();
        int bottom = y + layout.height();
        graphics.fill(x, y, right, bottom, 0xE9101118);
        graphics.fill(x, y, right, y + HEADER_HEIGHT, 0xFF252936);
        graphics.fill(x, y, right, y + 1, 0xFF65718A);
        graphics.fill(x, bottom - 1, right, bottom, 0xFF65718A);
        graphics.fill(x, y, x + 1, bottom, 0xFF65718A);
        graphics.fill(right - 1, y, right, bottom, 0xFF65718A);
        graphics.drawString(Minecraft.getInstance().font, Component.literal("Mods"), x + 8, y + 8, 0xFFFFFFFF, false);

        boolean clearHovered = layout.clearContains(mouseX, mouseY);
        graphics.fill(layout.clearX(), layout.clearY(), layout.clearX() + 12, layout.clearY() + 12,
            clearHovered ? 0xFF59647A : 0xFF3B4252);
        graphics.drawCenteredString(Minecraft.getInstance().font, Component.literal("×"),
            layout.clearX() + 6, layout.clearY() + 1, 0xFFFFFFFF);

        String currentFilter = runtime.getIngredientFilter().getFilterText();
        int listTop = layout.listTop();
        int listBottom = bottom - 5;
        graphics.enableScissor(x + 2, listTop, right - 2, listBottom);
        for (int row = 0; row < layout.visibleRows(); row++) {
            int index = scrollOffset + row;
            if (index >= entries.size()) break;
            ModEntry entry = entries.get(index);
            int rowY = listTop + row * ROW_HEIGHT;
            boolean hovered = mouseX >= x + 3 && mouseX < right - 3 &&
                mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean selected = currentFilter.equals("@" + entry.modId());
            if (selected) {
                graphics.fill(x + 3, rowY, right - 3, rowY + ROW_HEIGHT, 0xFF344C68);
                graphics.fill(x + 3, rowY, x + 5, rowY + ROW_HEIGHT, 0xFF76B9F5);
            } else if (hovered) {
                graphics.fill(x + 3, rowY, right - 3, rowY + ROW_HEIGHT, 0xFF303746);
            }
            drawModIcon(graphics, entry, x + 7, rowY + 2);
            String label = Minecraft.getInstance().font.plainSubstrByWidth(entry.displayName(), PANEL_WIDTH - 39);
            graphics.drawString(Minecraft.getInstance().font, label, x + 28, rowY + 6, 0xFFE8EAF0, false);
        }
        graphics.disableScissor();
        if (entries.isEmpty()) {
            graphics.drawString(Minecraft.getInstance().font, "No mods found", x + 8, listTop + 8, 0xFFB0B4C0, false);
        } else if (entries.size() > layout.visibleRows()) {
            String counter = (scrollOffset + 1) + "-" + Math.min(scrollOffset + layout.visibleRows(), entries.size())
                + "/" + entries.size();
            graphics.drawString(Minecraft.getInstance().font, counter, x + 8, bottom - 12, 0xFF9AA2B2, false);
        }
    }

    private static void drawModIcon(GuiGraphics graphics, ModEntry entry, int x, int y) {
        ResourceLocation icon = getIcon(entry);
        if (icon != null) {
            graphics.blit(icon, x, y, 0, 0, 16, 16, 16, 16);
            return;
        }
        int hash = entry.modId().hashCode();
        int color = 0xFF000000 | (0x505050 | (hash & 0x7F7F7F));
        graphics.fill(x, y, x + 16, y + 16, color);
        String initial = entry.displayName().isBlank() ? "?" : entry.displayName().substring(0, 1).toUpperCase();
        graphics.drawCenteredString(Minecraft.getInstance().font, initial, x + 8, y + 4, 0xFFFFFFFF);
    }

    private static ResourceLocation getIcon(ModEntry entry) {
        if (ICONS.containsKey(entry.modId())) return ICONS.get(entry.modId());
        if (ICON_LOOKUPS.putIfAbsent(entry.modId(), Boolean.TRUE) != null) return null;
        Optional<String> logoFile = entry.info().getLogoFile();
        if (logoFile.isEmpty() || logoFile.get().isBlank()) return null;
        try {
            Path logoPath = entry.info().getOwningFile().getFile().findResource(logoFile.get());
            if (!Files.isRegularFile(logoPath)) return null;
            try (InputStream input = Files.newInputStream(logoPath);
                 NativeImage source = NativeImage.read(input)) {
                NativeImage scaled = new NativeImage(16, 16, false);
                for (int targetY = 0; targetY < 16; targetY++) {
                    for (int targetX = 0; targetX < 16; targetX++) {
                        int sourceX = targetX * source.getWidth() / 16;
                        int sourceY = targetY * source.getHeight() / 16;
                        scaled.setPixelRGBA(targetX, targetY, source.getPixelRGBA(sourceX, sourceY));
                    }
                }
                DynamicTexture texture = new DynamicTexture(scaled);
                ResourceLocation location = Minecraft.getInstance().getTextureManager()
                    .register("jei_mod_filter/" + entry.modId(), texture);
                ICONS.put(entry.modId(), location);
                return location;
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private record ModEntry(String modId, String displayName, IModInfo info) {
    }

    private record PanelLayout(int x, int y, int width, int height, int clearX, int clearY) {
        int listTop() { return y + HEADER_HEIGHT; }
        int visibleRows() { return Math.max(1, (height - HEADER_HEIGHT - 8) / ROW_HEIGHT); }
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
        boolean clearContains(double mouseX, double mouseY) {
            return mouseX >= clearX && mouseX < clearX + 12 &&
                mouseY >= clearY && mouseY < clearY + 12;
        }
        boolean listContains(double mouseX, double mouseY) {
            return mouseX >= x + 3 && mouseX < x + width - 3 &&
                mouseY >= listTop() && mouseY < listTop() + visibleRows() * ROW_HEIGHT;
        }
    }
}
