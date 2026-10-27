/*
 *     Ayame PaperDoll DS Compat — DragonSurvival compatibility addon.
 *     Copyright (C) 2025-2026  HOZ, Deepseek
 *
 *     Based on Ayame PaperDoll, copyright (C) 2024-2025 LucunJi, HappyRespawnanchor.
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

package com.hoz.ayamedscompat.mixin;

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import com.geckolib.renderer.base.RenderPassInfo;
import com.hoz.ayamedscompat.AyameDSCompat;
import com.hoz.ayamedscompat.BoneCacheGuard;
import com.hoz.ayamedscompat.SeatCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the world, DragonRenderer offsets the model from the entity by an amount derived from
 * the live MovementData bodyYaw and the dragon's size. In the paper doll that live offset
 * makes the model orbit the preview origin when the view rotates, so we zero it during compat
 * rendering and let the paper doll's own translation center the model.
 */
@Mixin(value = DragonRenderer.class, remap = false)
public abstract class DragonRendererMixin {

    @Unique
    private double ayamedscompat$savedBodyYaw;
    @Unique
    private boolean ayamedscompat$bodyYawSynced;

    @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private void ayamedscompat$centerModel(DragonRenderer.DragonRenderData renderData,
                                           CallbackInfoReturnable<Vec3> cir) {
        if (AyameDSCompat.isRenderingForCompat()) {
            cir.setReturnValue(Vec3.ZERO);
            return;
        }

        // World: align a dragon passenger's model offset with the root mount instead of its
        // own (possibly stale on other clients) MovementData bodyYaw. Only rendering changes.
        if (!AyamePaperDoll.CONFIGS.syncPassengerBodyYaw.getValue()) {
            return;
        }
        Player player = renderData.player();
        if (player == null) {
            return;
        }
        Player root = ayamedscompat$rootDragonMount(player);
        if (root == null) {
            return;
        }

        double yaw = MovementData.getData(root).bodyYaw;
        float angle = -(float) yaw * ((float) Math.PI / 180.0F);
        float scale = (float) renderData.visualScale() * renderData.bodyScaleMultiplier();
        cir.setReturnValue(new Vec3(Math.sin(angle) * scale, 0, Math.cos(angle) * scale));
    }

    // World: align a dragon passenger's body to the root mount (DS only does this on the
    // passenger's own client). Paper doll: face the doll's locked pose, which is already
    // stored in the render data during extraction. Restored at RETURN so the animation and
    // the rest of the frame keep the real world value.
    @Inject(method = "setupRender", at = @At("HEAD"), remap = false)
    private void ayamedscompat$applyPassengerBodyYaw(DragonRenderer.DragonRenderData renderData,
                                                     PoseStack pose, CallbackInfo ci) {
        ayamedscompat$bodyYawSynced = false;
        if (AyameDSCompat.isRenderingForCompat()) {
            return;
        }
        if (!AyamePaperDoll.CONFIGS.syncPassengerBodyYaw.getValue()) {
            return;
        }
        Player player = renderData.player();
        if (player == null) {
            return;
        }
        Player root = ayamedscompat$rootDragonMount(player);
        if (root == null) {
            return;
        }

        DragonRenderDataAccessor accessor = (DragonRenderDataAccessor) (Object) renderData;
        ayamedscompat$savedBodyYaw = accessor.ds$getBodyYaw();
        // The root model and the MountingBone seat are rendered with the CURRENT body yaw;
        // using bodyYawLastFrame would lag the passenger one tick and rotate its tilt axes.
        double syncedYaw = MovementData.getData(root).bodyYaw;
        accessor.ds$setBodyYaw(syncedYaw);
        ayamedscompat$bodyYawSynced = true;
    }

    @Inject(method = "setupRender", at = @At("RETURN"), remap = false)
    private void ayamedscompat$restorePassengerBodyYaw(DragonRenderer.DragonRenderData renderData,
                                                       PoseStack pose, CallbackInfo ci) {
        if (!ayamedscompat$bodyYawSynced) {
            return;
        }
        ((DragonRenderDataAccessor) (Object) renderData).ds$setBodyYaw(ayamedscompat$savedBodyYaw);
        ayamedscompat$bodyYawSynced = false;
    }

    @Unique
    private static Player ayamedscompat$rootDragonMount(final Player player) {
        if (!(player.getVehicle() instanceof Player vehicle) || !DragonStateProvider.isDragon(vehicle)) {
            return null;
        }
        // Intermediate alignment is temporary; a deeper passenger must use the root's bodyYaw.
        Player root = vehicle;
        while (root.getVehicle() instanceof Player parent && DragonStateProvider.isDragon(parent)) {
            root = parent;
        }
        return root;
    }

    // The paper doll places riders with the captured MountingBone transform; suppress DS's
    // world-space correction during the paper-doll render.
    @Inject(method = "getMountingBonePositionCorrection(Lnet/minecraft/world/entity/player/Player;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ayamedscompat$suppressWorldSeatCorrection(final Player rider, final float partialTick,
                                                                  final CallbackInfoReturnable<Vec3> cir) {
        if (AyameDSCompat.isRenderingForCompat()) {
            cir.setReturnValue(null);
        }
    }

    // Paper-doll renders must not perturb DS's shared bone caches; also capture the
    // MountingBone tracked pose relative to the paper-doll node base while the deferred
    // model render task is running.
    @Inject(method = "preRenderPass", at = @At("TAIL"), remap = false)
    private void ayamedscompat$onPreRenderPass(final RenderPassInfo<?> renderPassInfo,
                                               final SubmitNodeCollector renderTasks,
                                               final CallbackInfo ci) {
        if (!AyameDSCompat.isRenderingForCompat()) {
            return;
        }
        DragonRenderer.DragonRenderData renderData = renderPassInfo.getGeckolibData(DragonRenderer.DRAGON_RENDER_DATA);
        if (renderData == null || renderData.player() == null) {
            return;
        }
        BoneCacheGuard.begin(renderData.dragonId());
        if (AyameDSCompat.isSeatCaptureActive()) {
            SeatCapture.install(renderPassInfo);
        }
    }

    // During compat, suppress the gliding pitch/roll applied in setupRender so the paper doll
    // dragon body stays facing forward instead of wobbling side to side (config ds_glide_wobble).
    @WrapOperation(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lby/dragonsurvivalteam/dragonsurvival/server/handlers/ServerFlightHandler;"
                   + "isGliding(Lnet/minecraft/world/entity/player/Player;)Z"),
            remap = false)
    private boolean ayamedscompat$forceRenderBodyInSetupRender(final Player player,
                                                               final Operation<Boolean> original) {
        if (AyameDSCompat.isRenderingForCompat() && !AyamePaperDoll.CONFIGS.dsGlideWobble.getValue()) {
            return false;
        }
        return original.call(player);
    }
}
