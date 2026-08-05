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
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DragonRenderer.class, remap = false)
public abstract class DragonRendererMixin {

    @Unique
    private double ayamedscompat$savedBodyYaw;
    @Unique
    private boolean ayamedscompat$needsRestore;

    // Anchor the dragon's body yaw to the paper doll's locked pose. This affects
    // the pose rotation applied inside setupRender itself; the head/neck pose is
    // anchored separately in DragonModelMixin at applyMolangQueries (the point
    // where the movement data actually reaches the animation).
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

    @WrapOperation(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lby/dragonsurvivalteam/dragonsurvival/server/handlers/ServerFlightHandler;"
                   + "isGliding(Lnet/minecraft/world/entity/player/Player;)Z"),
            remap = false)
    private boolean ayamedscompat$forceRenderBodyInSetupRender(final Player player,
                                                               final Operation<Boolean> original) {
        if (AyameDSCompat.isRenderingForCompat()) {
            return false;
        }
        return original.call(player);
    }
}
