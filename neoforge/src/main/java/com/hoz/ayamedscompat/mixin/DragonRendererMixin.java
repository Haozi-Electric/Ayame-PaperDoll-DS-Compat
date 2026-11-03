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

import by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler;
import com.hoz.ayamedscompat.DragonAnimationSampler;
import com.hoz.ayamedscompat.shared.AyameDSCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DragonRenderer.class, remap = false)
public abstract class DragonRendererMixin {

    @Unique
    private double ayamedscompat$savedBodyYaw;
    @Unique
    private float ayamedscompat$savedPrevXRot;
    @Unique
    private float ayamedscompat$savedPrevZRot;
    @Unique
    private boolean ayamedscompat$needsRestore;
    @Unique
    private boolean ayamedscompat$savedFlightRotations;
    @Unique
    private boolean ayamedscompat$setupSavedInInventory;
    @Unique
    private boolean ayamedscompat$setupForcedInventory;
    @Unique
    private boolean ayamedscompat$savedInInventory;
    @Unique
    private boolean ayamedscompat$restoreInInventory;

    // Zero the live model offset in the paper doll; otherwise the model orbits the preview origin.
    @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private void ayamedscompat$centerModel(final DragonEntity dragon, final float partialTicks,
                                           final CallbackInfoReturnable<Vec3> cir) {
        if (AyameDSCompat.isRenderingForCompat()) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }

    // DS 2.0.72 moved the shared BONE_ROTATIONS write from postRender into the render pass
    // (renderCubesOfBone). Skip it during compat so HUD poses do not replace the world's
    // MountingBone rotations. Older DS has no such method; require = 0 keeps this inert there.
    @Inject(method = "updateBoneRotation", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ayamedscompat$skipBoneRotationCache(final CallbackInfo ci) {
        if (AyameDSCompat.isRenderingForCompat()) {
            ci.cancel();
        }
    }

    // World: align a dragon passenger's body to the root mount (DS only does this on the
    // passenger's own client). Paper doll: face the doll's locked pose. Restored at RETURN so
    // the animation still sees the real world values.
    @Inject(method = "setupRender", at = @At("HEAD"), remap = false)
    private void ayamedscompat$overrideBodyYaw(final DragonEntity dragon, final Player player,
                                               final PoseStack pose, final float partialTick,
                                               final CallbackInfo ci) {
        if (player == null) {
            return;
        }
        if (!AyameDSCompat.isRenderingForCompat()) {
            if (AyamePaperDoll.CONFIGS.syncPassengerBodyYaw.getValue()
                    && ayamedscompat$syncPassengerBodyYaw(player, partialTick)) {
                ayamedscompat$useSampledFlightTilt(dragon, partialTick);
                ayamedscompat$forceLegacySetupRender(dragon);
            }
            return;
        }
        final MovementData movement = MovementData.getData(player);
        ayamedscompat$savedBodyYaw = movement.bodyYaw;
        // Interpolate the locked body yaw; using the raw tick value makes the doll visibly
        // step while turning. The world dragon gets the same smoothing from
        // animationState.sample(partialTick).
        movement.bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        if (!AyamePaperDoll.CONFIGS.dsGlideWobble.getValue()) {
            // Newer DS wings the glide condition off dragon.prevXRot/prevZRot as well. Zero
            // them so the release condition (isGliding forced false below AND prev == 0)
            // keeps the body level.
            ayamedscompat$setFlightTilt(dragon, 0.0F, 0.0F);
        } else {
            // DS updates prevXRot/prevZRot once per tick; reusing the raw values makes the
            // paper doll wobble at 20 Hz. Feed it the partial-tick-interpolated values the
            // world renderer samples instead.
            ayamedscompat$useSampledFlightTilt(dragon, partialTick);
        }
        ayamedscompat$forceLegacySetupRender(dragon);
        ayamedscompat$needsRestore = true;
    }

    // DS 2.0.72 samples dragon.animationState in setupRender unless the proxy is marked as
    // an inventory render. Force the MovementData/prevXRot path for this method only, so the
    // body-yaw pin and glide zeroing still apply; RETURN restores the flag before the model's
    // molang queries run, so tail/body motion animations stay live.
    @Unique
    private void ayamedscompat$forceLegacySetupRender(final DragonEntity dragon) {
        ayamedscompat$setupSavedInInventory = dragon.isInInventory;
        dragon.isInInventory = true;
        ayamedscompat$setupForcedInventory = true;
    }

    @Unique
    private void ayamedscompat$setFlightTilt(final DragonEntity dragon, final float pitch, final float roll) {
        ayamedscompat$savedPrevXRot = dragon.prevXRot;
        ayamedscompat$savedPrevZRot = dragon.prevZRot;
        dragon.prevXRot = pitch;
        dragon.prevZRot = roll;
        ayamedscompat$savedFlightRotations = true;
    }

    @Unique
    private void ayamedscompat$useSampledFlightTilt(final DragonEntity dragon, final float partialTick) {
        final float[] flight = DragonAnimationSampler.sampleFlight(dragon, partialTick);
        if (flight != null) {
            ayamedscompat$setFlightTilt(dragon, flight[0], flight[1]);
        }
    }

    @Inject(method = "setupRender", at = @At("RETURN"), remap = false)
    private void ayamedscompat$restoreBodyYaw(final DragonEntity dragon, final Player player,
                                              final PoseStack pose, final float partialTick,
                                              final CallbackInfo ci) {
        if (ayamedscompat$needsRestore && player != null) {
            final MovementData movement = MovementData.getData(player);
            movement.bodyYaw = ayamedscompat$savedBodyYaw;
            if (ayamedscompat$savedFlightRotations) {
                dragon.prevXRot = ayamedscompat$savedPrevXRot;
                dragon.prevZRot = ayamedscompat$savedPrevZRot;
                ayamedscompat$savedFlightRotations = false;
            }
            ayamedscompat$needsRestore = false;
        }
        if (ayamedscompat$setupForcedInventory) {
            dragon.isInInventory = ayamedscompat$setupSavedInInventory;
            ayamedscompat$setupForcedInventory = false;
        }
    }

    @Unique
    private boolean ayamedscompat$syncPassengerBodyYaw(final Player player, final float partialTick) {
        if (ayamedscompat$needsRestore || !player.isPassenger()) {
            return false;
        }
        if (!(player.getVehicle() instanceof Player vehicle) || !DragonStateProvider.isDragon(vehicle)) {
            return false;
        }
        // Intermediate alignment is temporary; a deeper passenger must use the root's bodyYaw.
        Player root = vehicle;
        while (root.getVehicle() instanceof Player parent && DragonStateProvider.isDragon(parent)) {
            root = parent;
        }
        final MovementData movement = MovementData.getData(player);
        // Match the root's rendered body yaw: use its partial-tick-interpolated sample when
        // the running DS exposes it, otherwise fall back to the current tick value.
        final DragonEntity rootDragon = ClientDragonRenderer.getDragon(root);
        final Double sampled = rootDragon == null
                ? null
                : DragonAnimationSampler.sampleBodyYaw(rootDragon, partialTick);
        final double yaw = sampled != null ? sampled : MovementData.getData(root).bodyYaw;
        ayamedscompat$savedBodyYaw = movement.bodyYaw;
        movement.bodyYaw = yaw;
        ayamedscompat$needsRestore = true;
        return true;
    }

    // Paper-doll bone poses live in HUD space. Skip DS's shared bone-cache write during compat
    // so the real MountingBone/seat data is not corrupted.
    @Inject(method = "postRender(Lcom/mojang/blaze3d/vertex/PoseStack;"
                         + "Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;"
                         + "Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;"
                         + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                         + "Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V",
            at = @At("HEAD"), remap = false, require = 1)
    private void ayamedscompat$skipPreviewBoneCache(final PoseStack pose, final DragonEntity dragon,
                                                    final BakedGeoModel model, final MultiBufferSource buffers,
                                                    final VertexConsumer buffer, final boolean isReRender,
                                                    final float partialTick, final int packedLight,
                                                    final int packedOverlay, final int color,
                                                    final CallbackInfo ci) {
        if (AyameDSCompat.isRenderingForCompat()) {
            ayamedscompat$savedInInventory = dragon.isInInventory;
            dragon.isInInventory = true;
            ayamedscompat$restoreInInventory = true;
        }
    }

    @Inject(method = "postRender(Lcom/mojang/blaze3d/vertex/PoseStack;"
                         + "Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;"
                         + "Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;"
                         + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                         + "Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V",
            at = @At("RETURN"), remap = false, require = 1)
    private void ayamedscompat$restoreBoneCacheGuard(final PoseStack pose, final DragonEntity dragon,
                                                     final BakedGeoModel model, final MultiBufferSource buffers,
                                                     final VertexConsumer buffer, final boolean isReRender,
                                                     final float partialTick, final int packedLight,
                                                     final int packedOverlay, final int color,
                                                     final CallbackInfo ci) {
        if (ayamedscompat$restoreInInventory) {
            dragon.isInInventory = ayamedscompat$savedInInventory;
            ayamedscompat$restoreInInventory = false;
        }
    }

    // The rider is already placed with the captured MountingBone transform; suppress DS's
    // world-space correction during the paper-doll render.
    @Inject(method = "getMountingBonePositionCorrection(Lnet/minecraft/world/entity/player/Player;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ayamedscompat$suppressWorldSeatCorrection(final Player rider, final float partialTick,
                                                                  final CallbackInfoReturnable<Vec3> cir) {
        if (AyameDSCompat.isRenderingForCompat()) {
            cir.setReturnValue(null);
        }
    }

    // Capture the MountingBone pose relative to the paper-doll node base.
    @Inject(method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;"
                         + "Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;"
                         + "Lsoftware/bernie/geckolib/cache/object/GeoBone;"
                         + "Lnet/minecraft/client/renderer/RenderType;"
                         + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                         + "Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V",
            at = @At("HEAD"), remap = false, require = 1)
    private void ayamedscompat$captureMountingBone(final PoseStack poseStack, final DragonEntity dragon,
                                                   final GeoBone bone, final RenderType renderType,
                                                   final MultiBufferSource bufferSource,
                                                   final VertexConsumer buffer, final boolean isReRender,
                                                   final float partialTick, final int packedLight,
                                                   final int packedOverlay, final int color,
                                                   final CallbackInfo ci) {
        // Literal: the compile-time DS jar does not expose DragonRidingHandler.MOUNTING_BONE.
        if (!AyameDSCompat.isSeatCaptureActive() || !"MountingBone".equals(bone.getName())) {
            return;
        }
        final Player player = dragon.getPlayer();
        if (player == null || !AyameDSCompat.isSeatCaptureTarget(player)) {
            return;
        }
        final PoseStack bonePose = new PoseStack();
        bonePose.last().pose().set(poseStack.last().pose());
        // Match GeoEntityRenderer's tracked matrix: stop before translateAwayFromPivotPoint.
        // prepMatrixForBone includes the translate-away and cancels the pivot translation.
        RenderUtil.translateMatrixToBone(bonePose, bone);
        RenderUtil.translateToPivotPoint(bonePose, bone);
        RenderUtil.rotateMatrixAroundBone(bonePose, bone);
        RenderUtil.scaleMatrixForBone(bonePose, bone);
        AyameDSCompat.captureSeatMatrix(bonePose.last().pose());
    }

    // During compat, suppress the gliding pitch/roll applied in setupRender so the paper doll
    // dragon stays level (config ds_glide_wobble). When the wobble is enabled, the real
    // isGliding value is returned so the body pitches/banks like the world dragon.
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
