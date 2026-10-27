/*
 *     Highly configurable PaperDoll mod. Forked from Extra Player Renderer.
 *     Copyright (C) 2024-2025  LucunJi(Original author), HappyRespawnanchor
 *
 *     This file is part of Ayame PaperDoll.
 *
 *     Ayame PaperDoll is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Ayame PaperDoll is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with Ayame PaperDoll.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.ayamemc.ayamepaperdoll.hud;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import com.hoz.ayamedscompat.AyameDSCompat;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import org.ayamemc.ayamepaperdoll.hud.ModRenderState.ChainChild;
import org.ayamemc.ayamepaperdoll.hud.ModRenderState.ChainNode;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.ayamemc.ayamepaperdoll.AyamePaperDoll.CONFIGS;

public class ModRenderer extends PictureInPictureRenderer<ModRenderState> {
    private static long lastChainDebugLogNanos;
    private final ProjectionMatrixBuffer projectionMatrixBuffer = new ProjectionMatrixBuffer("PIP - " + this.getClass().getSimpleName());
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final Projection projection = new Projection();

    private int width, height;

    public ModRenderer(MultiBufferSource.BufferSource bufferSource, EntityRenderDispatcher entityRenderDispatcher) {
        super(bufferSource);
        this.entityRenderDispatcher = entityRenderDispatcher;
    }

    @Override
    public @NotNull Class<ModRenderState> getRenderStateClass() {
        return ModRenderState.class;
    }

    @Override
    protected void renderToTexture(ModRenderState renderState, PoseStack poseStack) {
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        Quaternionf quaternionf = renderState.overrideCameraAngle();
        FeatureRenderDispatcher featurerenderdispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
        CameraRenderState camerarenderstate = new CameraRenderState();

        if (quaternionf != null) {
            camerarenderstate.orientation = quaternionf.conjugate(new Quaternionf()).rotateY((float) Math.PI);
        }
        if (renderState.chain() != null) {
            renderChain(renderState, poseStack, camerarenderstate, featurerenderdispatcher);
            return;
        }
        if(renderState.vehicleRenderState() != null){
            poseStack.pushPose();
            Vector3f vector3f = renderState.translation2();
            assert vector3f != null;
            poseStack.mulPose(renderState.rotation2());
            this.entityRenderDispatcher.submit(renderState.vehicleRenderState(), camerarenderstate, vector3f.x, vector3f.y, vector3f.z, poseStack, featurerenderdispatcher.getSubmitNodeStorage());
            poseStack.popPose();
        }
        Vector3f vector3f = renderState.translation();
        poseStack.mulPose(renderState.rotation());
        // Mark DS compat rendering (only when the preview target is a dragon) across submit AND
        // renderAllFeatures: submit only queues the node, the actual bone pass (which reads
        // Compat.displayNeck for the neck visibility) runs in renderAllFeatures, so the flag must
        // stay up through that. Normal human paper doll renders never set the flag.
        boolean dragonTarget = AyameDSCompat.isCompatTargetDragon();
        if (dragonTarget) {
            AyameDSCompat.pushCompatRendering();
        }
        try {
            this.entityRenderDispatcher.submit(renderState.renderState(), camerarenderstate, vector3f.x, vector3f.y, vector3f.z, poseStack, featurerenderdispatcher.getSubmitNodeStorage());
            featurerenderdispatcher.renderAllFeatures();
        } finally {
            AyameDSCompat.afterCompatRenderPass();
            if (dragonTarget) {
                AyameDSCompat.popCompatRendering();
            }
        }
    }

    // Each node is submitted and the deferred feature pass forced, so its MountingBone can be
    // captured before placing the children (bone matrices only exist during the model render).
    private void renderChain(ModRenderState renderState, PoseStack poseStack,
                             CameraRenderState camera, FeatureRenderDispatcher featureDispatcher) {
        poseStack.pushPose();
        try {
            poseStack.mulPose(renderState.rotation());
            if (renderState.groupScale() != 1.0f) {
                poseStack.scale(renderState.groupScale(), renderState.groupScale(), renderState.groupScale());
            }
            if (renderState.fitScale() != 1.0f) {
                poseStack.scale(renderState.fitScale(), renderState.fitScale(), renderState.fitScale());
            }
            AyameDSCompat.pushCompatRendering();
            try {
                renderChainNode(renderState.chain(), poseStack, camera, featureDispatcher);
            } finally {
                AyameDSCompat.popCompatRendering();
            }
        } finally {
            poseStack.popPose();
        }
    }

    private void renderChainNode(ChainNode node, PoseStack poseStack,
                                 CameraRenderState camera, FeatureRenderDispatcher featureDispatcher) {
        poseStack.pushPose();
        try {
            Vector3f translation = node.translation();
            if (translation.x != 0 || translation.y != 0 || translation.z != 0) {
                poseStack.translate(translation.x, translation.y, translation.z);
            }

            boolean captureSeat = node.dragon() && !node.children().isEmpty();
            if (captureSeat) {
                AyameDSCompat.beginSeatCapture(poseStack.last().pose());
            }

            AyameDSCompat.SeatTransform seat = null;
            try {
                this.entityRenderDispatcher.submit(node.renderState(), camera, 0.0, 0.0, 0.0,
                        poseStack, featureDispatcher.getSubmitNodeStorage());
                featureDispatcher.renderAllFeatures();
                if (captureSeat) {
                    seat = AyameDSCompat.takeSeatTransform();
                }
            } finally {
                AyameDSCompat.afterCompatRenderPass();
                if (captureSeat) {
                    AyameDSCompat.endSeatCapture();
                }
            }

            for (ChainChild child : node.children()) {
                poseStack.pushPose();
                try {
                    if (seat != null) {
                        // translate(seat) * rotate(bone tilt) * translate(-pivot). DS's render
                        // correction wins over CarryOn when the mount has a MountingBone.
                        poseStack.translate(seat.position().x, seat.position().y, seat.position().z);
                        poseStack.mulPose(seat.tilt());
                        Vector3f pivot = child.pivot();
                        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
                        logChainTransform("[Debug] chain {} -> {}: seat=({}, {}, {}), tilt=({}, {}, {}, {}), pivot=({}, {}, {})",
                                node.label(), child.node().label(),
                                seat.position(), seat.tilt(), child.pivot());
                    } else if (child.carryOnOffset() != null) {
                        // No MountingBone: CarryOn's front-carry offset is what the world shows.
                        Vector3f carryOffset = child.carryOnOffset();
                        poseStack.translate(carryOffset.x, carryOffset.y, carryOffset.z);
                        logChainTransform("[Debug] chain (CarryOn) {} -> {}: offset=({}, {}, {})",
                                node.label(), child.node().label(), carryOffset);
                    } else {
                        // DS fallback without a MountingBone: vanilla PASSENGER point, no tilt.
                        Vector3f attachment = child.fallbackAttachment();
                        poseStack.translate(attachment.x, attachment.y, attachment.z);
                        Vector3f pivot = child.pivot();
                        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
                        logChainTransform("[Debug] chain (no MountingBone fallback) {} -> {}: attachment=({}, {}, {})",
                                node.label(), child.node().label(), attachment);
                    }
                    renderChainNode(child.node(), poseStack, camera, featureDispatcher);
                } finally {
                    poseStack.popPose();
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void logChainTransform(String format, String carrier, String rider,
                                          Vector3f position, Quaternionf tilt, Vector3f pivot) {
        if (!CONFIGS.debugMode.getValue()) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastChainDebugLogNanos <= 1_000_000_000L) {
            return;
        }
        lastChainDebugLogNanos = now;
        AyamePaperDoll.LOGGER.info(format, carrier, rider,
                position.x, position.y, position.z,
                tilt.x, tilt.y, tilt.z, tilt.w,
                pivot.x, pivot.y, pivot.z);
    }

    private static void logChainTransform(String format, String carrier, String rider, Vector3f vector) {
        if (!CONFIGS.debugMode.getValue()) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastChainDebugLogNanos > 1_000_000_000L) {
            lastChainDebugLogNanos = now;
            AyamePaperDoll.LOGGER.info(format, carrier, rider, vector.x, vector.y, vector.z);
        }
    }

    @Override
    protected @NotNull String getTextureLabel() {
        return "ayame-paperdoll";
    }

    @Override
    public void prepare(ModRenderState renderState, GuiRenderState guiRenderState, int guiScale) {
        var width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        var height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        var raw_width = Minecraft.getInstance().getWindow().getWidth();
        var raw_height = Minecraft.getInstance().getWindow().getHeight();
        boolean needsAResize = this.width != width || this.height != height;
        if (needsAResize) {
            this.width = width;
            this.height = height;
        }
        this.prepareTexturesAndProjection(true, raw_width, raw_height);
        this.projection.setupOrtho(-1000.0F, 1000.0F, width, height, true);
        RenderSystem.setProjectionMatrix(this.projectionMatrixBuffer.getBuffer(this.projection), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.outputColorTextureOverride = this.textureView;
        RenderSystem.outputDepthTextureOverride = this.depthTextureView;
        PoseStack posestack = new PoseStack();
        float x0 =CONFIGS.mirrored.getValue()? width - renderState.x0() : renderState.x0();
        posestack.translate(x0, renderState.y0(), 0.0F);
        float f =  renderState.scale();
        posestack.scale(f, f, -f);
        this.renderToTexture(renderState, posestack);
        this.bufferSource.endBatch();
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
        blitTexture(renderState, guiRenderState);
    }

    @Override
    protected void blitTexture(final ModRenderState renderState, final GuiRenderState guiRenderState) {
        float u0,u1;
        if(CONFIGS.mirrored.getValue()){
            u0 = 1.0f;
            u1 = 0.0f;
        } else {
            u0 = 0.0f;
            u1 = 1.0f;
        }
        guiRenderState.addGlyphToCurrentLayer(
                new ExBlitRenderState(
                        AyamePaperDoll.MOD_PIPELINE,
                        new TextureSetup(
                                this.textureView, null, Minecraft.getInstance().gameRenderer.levelLightmap(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST), null, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        ),
                        renderState.pose(),
                        0,0, width, height,
                        u0,u1,
                        1.0F,
                        0.0F,
                        renderState.renderState().lightCoords,
                        renderState.scissorArea(),
                        null
                )
        );
    }
}