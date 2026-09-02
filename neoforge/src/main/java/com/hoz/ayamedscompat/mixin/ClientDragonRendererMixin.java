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
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler;
import com.hoz.ayamedscompat.AyameDSCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the paper doll dragon with the SAME data the third-person render uses.
 *
 * DS's renderDragon prepares the dragon each frame (setDragonMovementData + handleFlightMovement).
 * In the paper doll (compat):
 *  - Third person: the world render already prepared the dragon this frame, so re-preparing it
 *    double-advances the angles and feeds the animation a bodyYaw that lags the view (frozen head,
 *    tail bending with the view deviation) — we skip the prepare step and use the world's data.
 *  - First-person glide: the world render deliberately skips the dragon body, so nothing was
 *    prepared this frame — we run the prepare step so the first-person paper doll shows the
 *    third-person glide dragon.
 *
 * DS version differences: the old DS calls setDragonMovementData / handleFlightMovement inside
 * renderDragon; the new flight system (v2.0.68+) moved them into prepareDragonRenderState. We
 * wrap the old call sites with require=0 (silently skipped when absent) and inject at
 * prepareDragonRenderState HEAD for the new version, keeping the mod compatible with both.
 *
 * isGliding is still forced false in the renderDragon condition so the paper doll always renders
 * the dragon; the glide pitch/roll is handled by DragonRendererMixin.setupRender.
 */
@Mixin(value = ClientDragonRenderer.class, remap = false)
public abstract class ClientDragonRendererMixin {

    // True when the world render will NOT have prepared the dragon this frame: the local player
    // is gliding in first person with first-person-flight rendering disabled (DS skips the body).
    @Unique
    private static boolean ayamedscompat$worldDidNotPrepareDragon(final Player player) {
        return player == Minecraft.getInstance().player
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && ServerFlightHandler.isGliding(player)
                && !ClientDragonRenderer.renderFirstPersonFlight;
    }

    // New DS (2.0.68+): prepareDragonRenderState bundles setDragonMovementData + handleFlightMovement.
    // Skip the whole step in third-person compat; let it run in first-person glide.
    @Inject(method = "prepareDragonRenderState", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ayamedscompat$skipPrepareInThirdPerson(final Player player, final DragonEntity dragon,
                                                               final float partialTick, final CallbackInfo ci) {
        if (AyameDSCompat.isRenderingForCompat() && !ayamedscompat$worldDidNotPrepareDragon(player)) {
            ci.cancel();
        }
    }

    // Old DS: setDragonMovementData is called directly in renderDragon. Wrapped with require=0 so the
    // injection is absent (and harmless) under the new DS, where the call moved to prepareDragonRenderState.
    @WrapOperation(method = "renderDragon", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lby/dragonsurvivalteam/dragonsurvival/client/render/ClientDragonRenderer;"
                           + "setDragonMovementData(Lnet/minecraft/world/entity/player/Player;F)V"),
            remap = false)
    private static void ayamedscompat$conditionalSetDragonMovementData(final Player player, final float realtimeDeltaTick,
                                                                       final Operation<Void> original) {
        if (!AyameDSCompat.isRenderingForCompat()) {
            original.call(player, realtimeDeltaTick);
        } else if (ayamedscompat$worldDidNotPrepareDragon(player)) {
            original.call(player, realtimeDeltaTick);
        }
    }

    // Old DS: handleFlightMovement is called directly in renderDragon. Same require=0 note as above.
    @WrapOperation(method = "renderDragon", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lby/dragonsurvivalteam/dragonsurvival/client/render/ClientDragonRenderer;"
                           + "handleFlightMovement(Lnet/minecraft/world/entity/player/Player;"
                           + "Lby/dragonsurvivalteam/dragonsurvival/common/entity/DragonEntity;"
                           + "Lby/dragonsurvivalteam/dragonsurvival/registry/attachments/MovementData;F)V"),
            remap = false)
    private static void ayamedscompat$conditionalHandleFlightMovement(final Player player, final DragonEntity dragon,
                                                                      final MovementData movement, final float partialTick,
                                                                      final Operation<Void> original) {
        if (!AyameDSCompat.isRenderingForCompat()) {
            original.call(player, dragon, movement, partialTick);
        } else if (ayamedscompat$worldDidNotPrepareDragon(player)) {
            original.call(player, dragon, movement, partialTick);
        }
    }

    @WrapOperation(method = "renderDragon", at = @At(value = "INVOKE",
            target = "Lby/dragonsurvivalteam/dragonsurvival/server/handlers/ServerFlightHandler;"
                   + "isGliding(Lnet/minecraft/world/entity/player/Player;)Z"),
            remap = false)
    private static boolean ayamedscompat$forceRenderBody(final Player player,
                                                         final Operation<Boolean> original) {
        if (AyameDSCompat.isRenderingForCompat()) {
            return false;
        }
        return original.call(player);
    }
}
