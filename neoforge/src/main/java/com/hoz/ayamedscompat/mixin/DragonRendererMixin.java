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
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler;
import com.hoz.ayamedscompat.AyameDSCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
    private double ayamedscompat$savedBodyYawLastFrame;
    @Unique
    private float ayamedscompat$savedPrevXRot;
    @Unique
    private float ayamedscompat$savedPrevZRot;
    @Unique
    private boolean ayamedscompat$needsRestore;
    @Unique
    private boolean ayamedscompat$savedFlightRotations;
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
            if (AyamePaperDoll.CONFIGS.syncPassengerBodyYaw.getValue()) {
                ayamedscompat$syncPassengerBodyYaw(player);
            }
            return;
        }
        final MovementData movement = MovementData.getData(player);
        ayamedscompat$savedBodyYaw = movement.bodyYaw;
        ayamedscompat$savedBodyYawLastFrame = movement.bodyYawLastFrame;
        movement.bodyYaw = player.yBodyRot;
        // Newer DS wings the glide condition off dragon.prevXRot/prevZRot as well. When the
        // wobble is disabled, zero them so the release condition (isGliding forced false below
        // AND prevXRot/prevZRot == 0) keeps the body level; restore them at RETURN.
        if (!AyamePaperDoll.CONFIGS.dsGlideWobble.getValue()) {
            ayamedscompat$savedPrevXRot = dragon.prevXRot;
            ayamedscompat$savedPrevZRot = dragon.prevZRot;
            dragon.prevXRot = 0;
            dragon.prevZRot = 0;
            ayamedscompat$savedFlightRotations = true;
        }
        ayamedscompat$needsRestore = true;
    }

    @Inject(method = "setupRender", at = @At("RETURN"), remap = false)
    private void ayamedscompat$restoreBodyYaw(final DragonEntity dragon, final Player player,
                                              final PoseStack pose, final float partialTick,
                                              final CallbackInfo ci) {
        if (!ayamedscompat$needsRestore || player == null) {
            return;
        }
        final MovementData movement = MovementData.getData(player);
        movement.bodyYaw = ayamedscompat$savedBodyYaw;
        movement.bodyYawLastFrame = ayamedscompat$savedBodyYawLastFrame;
        if (ayamedscompat$savedFlightRotations) {
            dragon.prevXRot = ayamedscompat$savedPrevXRot;
            dragon.prevZRot = ayamedscompat$savedPrevZRot;
            ayamedscompat$savedFlightRotations = false;
        }
        ayamedscompat$needsRestore = false;
    }

    @Unique
    private void ayamedscompat$syncPassengerBodyYaw(final Player player) {
        if (ayamedscompat$needsRestore || !player.isPassenger()) {
            return;
        }
        if (!(player.getVehicle() instanceof Player vehicle) || !DragonStateProvider.isDragon(vehicle)) {
            return;
        }
        // Intermediate alignment is temporary; a deeper passenger must use the root's bodyYaw.
        Player root = vehicle;
        while (root.getVehicle() instanceof Player parent && DragonStateProvider.isDragon(parent)) {
            root = parent;
        }
        final MovementData movement = MovementData.getData(player);
        final MovementData rootMovement = MovementData.getData(root);
        ayamedscompat$savedBodyYaw = movement.bodyYaw;
        ayamedscompat$savedBodyYawLastFrame = movement.bodyYawLastFrame;
        final double yaw = rootMovement.bodyYawLastFrame;
        movement.bodyYaw = yaw;
        movement.bodyYawLastFrame = yaw;
        ayamedscompat$needsRestore = true;
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
