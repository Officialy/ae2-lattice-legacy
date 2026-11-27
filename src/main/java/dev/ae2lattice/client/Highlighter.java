package dev.ae2lattice.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Purely visual: draws a pulsing outline (visible through walls) around a machine. */
public final class Highlighter {
    private record Highlight(BlockPos pos, ResourceLocation dim, long untilMs) {}

    private static final List<Highlight> ACTIVE = new ArrayList<>();

    public static void highlight(BlockPos pos, ResourceLocation dim, int seconds) {
        ACTIVE.removeIf(h -> h.pos.equals(pos) && h.dim.equals(dim));
        ACTIVE.add(new Highlight(pos.immutable(), dim, Util.getMillis() + seconds * 1000L));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ACTIVE.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = Util.getMillis();
        ACTIVE.removeIf(h -> now > h.untilMs);
        var dim = mc.level.dimension().location();

        var cam = e.getCamera().getPosition();
        var pose = e.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(LatticeRenderTypes.HIGHLIGHT_LINES);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        float pulse = 0.55f + 0.45f * (float) Math.sin(now / 160.0);
        double grow = 0.06 + 0.05 * Math.sin(now / 220.0);
        for (var h : ACTIVE) {
            if (!h.dim.equals(dim)) continue;
            var box = new AABB(h.pos).inflate(0.01);
            LevelRenderer.renderLineBox(pose, lines, box, 1f, 0.9f, 0.3f, pulse);
            LevelRenderer.renderLineBox(pose, lines, box.inflate(grow), 0f, 0.71f, 0.85f, pulse * 0.8f);
        }
        pose.popPose();
        buffers.endBatch(LatticeRenderTypes.HIGHLIGHT_LINES);
    }

    private Highlighter() {}
}
