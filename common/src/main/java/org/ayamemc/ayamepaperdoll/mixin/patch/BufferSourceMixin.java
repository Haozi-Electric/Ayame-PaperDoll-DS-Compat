package org.ayamemc.ayamepaperdoll.mixin.patch;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import org.ayamemc.ayamepaperdoll.mixininterface.BufferSourceMixinInterface;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceMixin implements BufferSourceMixinInterface {

    @Unique
    private static final Logger AYAME_DEBUG_LOG = AyamePaperDoll.LOGGER;

    @Unique
    private boolean ayame_PaperDoll$flipCullFace = false;

    @Override
    public void ayame_PaperDoll$setFlipCullFace(boolean flipCullFace) {
        if (AyamePaperDoll.CONFIGS.debugMode.getValue()
                && AyamePaperDoll.CONFIGS.debugLogCullFace.getValue()) {
            AYAME_DEBUG_LOG.info("[Debug] setFlipCullFace: {} -> {}",
                    this.ayame_PaperDoll$flipCullFace, flipCullFace);
        }
        this.ayame_PaperDoll$flipCullFace = flipCullFace;
    }

    @WrapOperation(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V"))
    void disableCulling(MultiBufferSource.BufferSource instance, RenderType layer, BufferBuilder builder, Operation<Void> original) {
        final boolean debugMode = AyamePaperDoll.CONFIGS.debugMode.getValue();
        final boolean logRenderTypes = AyamePaperDoll.CONFIGS.debugLogRenderTypes.getValue();
        final boolean logCullFace = AyamePaperDoll.CONFIGS.debugLogCullFace.getValue();
        final boolean forceDisableCulling = AyamePaperDoll.CONFIGS.debugForceDisableCulling.getValue();

        if (debugMode && logRenderTypes) {
            AYAME_DEBUG_LOG.info("[Debug] endBatch layer: {} | flipCullFace: {} | forceDisableCulling: {}",
                    layer, this.ayame_PaperDoll$flipCullFace, forceDisableCulling);
        }

        if (!this.ayame_PaperDoll$flipCullFace && !(debugMode && forceDisableCulling)) {
            original.call(instance, layer, builder);
            return;
        }

        if (debugMode && forceDisableCulling) {
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (logCullFace) {
                boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                AYAME_DEBUG_LOG.info("[Debug] forceDisableCulling: glIsEnabled(CULL_FACE)={} (should be false)", cullEnabled);
            }
            original.call(instance, layer, builder);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            return;
        }

        // Mirror X-flip reverses winding, combined with the paper doll's
        // GPU Z-inversion front faces end up CW in window space. Default
        // culling (GL_BACK) would cull them, so flip to GL_FRONT.
        if (logCullFace) {
            boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            int cullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
            AYAME_DEBUG_LOG.info("[Debug] Before flip: cullEnabled={} cullMode={} (GL_BACK=0x405 GL_FRONT=0x404)",
                    cullEnabled, String.format("0x%X", cullMode));
        }

        GL11.glCullFace(GL11.GL_FRONT);

        if (logCullFace) {
            int cullModeAfter = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
            AYAME_DEBUG_LOG.info("[Debug] After glCullFace(GL_FRONT): cullMode={}",
                    String.format("0x%X", cullModeAfter));
        }

        original.call(instance, layer, builder);

        if (logCullFace) {
            int cullModeAfterDraw = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
            if (cullModeAfterDraw != GL11.GL_FRONT) {
                AYAME_DEBUG_LOG.warn(
                        "[Debug] CULL FACE CHANGED DURING DRAW: expected GL_FRONT(0x404) but got 0x{}!"
                                + " Another mod's mixin may be overriding glCullFace inside endBatch.",
                        String.format("%X", cullModeAfterDraw));
            }
        }

        GL11.glCullFace(GL11.GL_BACK);
    }
}
