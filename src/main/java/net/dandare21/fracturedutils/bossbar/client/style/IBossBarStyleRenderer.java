package net.dandare21.fracturedutils.bossbar.client.style;

import net.dandare21.fracturedutils.bossbar.client.ClientBossBarData;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Modular renderer interface for custom bossbar styles.
 */
public interface IBossBarStyleRenderer {

    /**
     * Unique identifier for this style (e.g. "default", "fractured", "custom_texture").
     */
    String getStyleId();

    /**
     * Total vertical space consumed by this bossbar in pixels (including name and padding).
     */
    int getTotalHeight(ClientBossBarData bar);

    /**
     * Renders the bossbar at the given screen coordinates.
     * @param graphics GuiGraphics instance
     * @param bar Bossbar client data
     * @param x Center-x coordinate for the bar
     * @param y Top-y coordinate for the bar
     * @param partialTick Render partial tick
     */
    void render(GuiGraphics graphics, ClientBossBarData bar, int x, int y, float partialTick);
}
