package io.github.sponeru.execore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.sponeru.execore.ConfiguredOreBlock;
import io.github.sponeru.execore.ExampleMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class OreScannerHighlightRenderer
{
    public static final int HIGHLIGHT_TICKS = 200;
    private static final float LINE_WIDTH = 3.0F;
    private static List<BlockPos> highlightedPositions = List.of();
    private static ClientLevel highlightedLevel;
    private static long expiresAt;

    private OreScannerHighlightRenderer()
    {
    }

    public static void show(List<BlockPos> positions)
    {
        Minecraft minecraft = Minecraft.getInstance();
        highlightedPositions = List.copyOf(positions);
        highlightedLevel = minecraft.level;
        expiresAt = minecraft.level == null ? 0L : minecraft.level.getGameTime() + HIGHLIGHT_TICKS;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || highlightedPositions.isEmpty())
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;

        if (level == null || level != highlightedLevel || level.getGameTime() >= expiresAt)
        {
            clear();
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        float pulse = 0.72F + 0.28F * (float) Math.sin((level.getGameTime() + event.getPartialTick()) * 0.25D);

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(LINE_WIDTH);
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);

        for (BlockPos position : highlightedPositions)
        {
            BlockState state = level.getBlockState(position);

            if (!(state.getBlock() instanceof ConfiguredOreBlock configuredOre) || !configuredOre.isDense())
            {
                continue;
            }

            int color = configuredOre.oreColor();
            float red = ((color >> 16) & 0xFF) / 255.0F;
            float green = ((color >> 8) & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;
            AABB bounds = new AABB(position).inflate(0.003D);
            LevelRenderer.renderLineBox(poseStack, buffer, bounds, red, green, blue, pulse);
        }

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.lineWidth(1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void clear()
    {
        highlightedPositions = List.of();
        highlightedLevel = null;
        expiresAt = 0L;
    }
}
