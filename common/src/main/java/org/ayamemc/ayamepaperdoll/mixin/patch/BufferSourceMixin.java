package org.ayamemc.ayamepaperdoll.mixin.patch;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.ayamemc.ayamepaperdoll.mixininterface.BufferSourceMixinInterface;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceMixin implements BufferSourceMixinInterface {

    @Unique
    private boolean ayame_PaperDoll$flipCullFace = false;

    @Override
    public void ayame_PaperDoll$setFlipCullFace(boolean flipCullFace) {
        this.ayame_PaperDoll$flipCullFace = flipCullFace;
    }

    // Wrap the per-layer draw call AFTER setupRenderState() has run.
    // DS's entityCutout render type explicitly enables culling in
    // setupRenderState(), so we must override the GL state at draw time.
    // Direct GL11 bypasses RenderSystem's lazy state tracking.
    @WrapOperation(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V"))
    void disableCulling(MultiBufferSource.BufferSource instance, RenderType layer, BufferBuilder builder, Operation<Void> original) {
        if (this.ayame_PaperDoll$flipCullFace) {
            // The paper doll's mirror X-flip reflects triangle winding, so the
            // model's front faces end up clockwise in window space. Default
            // culling (keep CCW) would cull them, making the model render
            // inside-out. Cull the CCW faces instead to keep the front visible
            // while still preventing the paper-thin dragon wings from
            // z-fighting (flickering).
            GL11.glCullFace(GL11.GL_FRONT);
            original.call(instance, layer, builder);
            GL11.glCullFace(GL11.GL_BACK);
        } else {
            original.call(instance, layer, builder);
        }
    }
}
