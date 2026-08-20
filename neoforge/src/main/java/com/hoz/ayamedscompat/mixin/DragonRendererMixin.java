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
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler;
import com.hoz.ayamedscompat.AyameDSCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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
    private boolean ayamedscompat$needsRestore;

    // In-world, DragonRenderer offsets the model from the entity by an amount derived from the
    // live MovementData bodyYaw and the dragon's size. In the paper doll that live offset makes
    // the model orbit the preview origin when the view rotates, so we zero it during compat
    // rendering and let the paper doll's own translation center the model.
    @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private void ayamedscompat$centerModel(final DragonEntity dragon, final float partialTicks,
                                           final CallbackInfoReturnable<Vec3> cir) {
        if (AyameDSCompat.isRenderingForCompat()) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }

    // Anchor the dragon's body yaw to the paper doll's locked pose (player.yBodyRot) so the
    // body faces the camera. This only affects the rotation applied inside setupRender; it is
    // restored at RETURN so the animation (applyMolangQueries, which runs afterwards) still
    // sees the real world bodyYaw and the tail keeps its natural swing.
    @Inject(method = "setupRender", at = @At("HEAD"), remap = false)
    private void ayamedscompat$overrideBodyYaw(final DragonEntity dragon, final Player player,
                                               final PoseStack pose, final float partialTick,
                                               final CallbackInfo ci) {
        if (!AyameDSCompat.isRenderingForCompat() || player == null) {
            return;
        }
        final MovementData movement = MovementData.getData(player);
        ayamedscompat$savedBodyYaw = movement.bodyYaw;
        movement.bodyYaw = player.yBodyRot;
        ayamedscompat$needsRestore = true;
    }

    @Inject(method = "setupRender", at = @At("RETURN"), remap = false)
    private void ayamedscompat$restoreBodyYaw(final DragonEntity dragon, final Player player,
                                              final PoseStack pose, final float partialTick,
                                              final CallbackInfo ci) {
        if (!ayamedscompat$needsRestore || player == null) {
            return;
        }
        MovementData.getData(player).bodyYaw = ayamedscompat$savedBodyYaw;
        ayamedscompat$needsRestore = false;
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
