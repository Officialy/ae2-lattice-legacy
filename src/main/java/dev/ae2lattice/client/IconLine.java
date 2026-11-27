package dev.ae2lattice.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.joml.Matrix4f;

/** A tooltip line with an item/fluid icon in front of it. */
public record IconLine(AEKey key, Component text) implements TooltipComponent {

    public static final class Client implements ClientTooltipComponent {
        private final IconLine line;

        public Client(IconLine line) {
            this.line = line;
        }

        @Override
        public int getHeight() {
            return 18;
        }

        @Override
        public int getWidth(Font font) {
            return 20 + font.width(line.text());
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            AEKeyRendering.drawInGui(Minecraft.getInstance(), g, x, y, line.key());
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffers) {
            font.drawInBatch(line.text(), x + 20, y + 4, -1, true, matrix, buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        }
    }
}
