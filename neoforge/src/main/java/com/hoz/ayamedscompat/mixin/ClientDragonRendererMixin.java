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
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler;
import com.hoz.ayamedscompat.AyameDSCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the paper doll dragon on the same preparation path as the in-world render and syncs the
 * client-side proxy entity's hurt/death state for rendering.
 */
@Mixin(value = ClientDragonRenderer.class, remap = false)
public abstract class ClientDragonRendererMixin {

    @Unique
    private static boolean ayamedscompat$worldDidNotPrepareDragon(final Player player) {
        return player == Minecraft.getInstance().player
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && ServerFlightHandler.isGliding(player)
                && !ClientDragonRenderer.renderFirstPersonFlight;
    }

    // DS 2.0.68+ prepares the dragon in prepareDragonRenderState; avoid the duplicate pass in compat
    // unless the world skipped it for first-person gliding.
    @Inject(method = "prepareDragonRenderState", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ayamedscompat$skipPrepareInThirdPerson(final Player player, final DragonEntity dragon,
                                                               final float partialTick, final CallbackInfo ci) {
        if (AyameDSCompat.isRenderingForCompat() && !ayamedscompat$worldDidNotPrepareDragon(player)) {
            ci.cancel();
        }
    }

    // Older DS versions call these directly from renderDragon; require = 0 keeps the wraps inert there.
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

    // Older DS versions call this directly from renderDragon; inert when the method is absent.
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

    // DS renders a client-side DragonEntity proxy, so the player's hurtTime never reaches the
    // model and GeckoLib's hurt overlay stays off. Sync it for the duration of the render.
    @Unique
    private static DragonEntity ayamedscompat$syncedDragon;
    @Unique
    private static int ayamedscompat$savedHurtTime;
    @Unique
    private static int ayamedscompat$savedDeathTime;

    @Inject(method = "renderDragon", at = @At("HEAD"), remap = false)
    private static void ayamedscompat$syncHurtFlash(final RenderPlayerEvent.Pre event, final CallbackInfo ci) {
        ayamedscompat$syncedDragon = null;
        if (!(event.getEntity() instanceof Player player) || !DragonStateProvider.isDragon(player)) {
            return;
        }
        DragonEntity dragon = ClientDragonRenderer.getOrCreateDragon(player);
        if (dragon == null) {
            return;
        }
        ayamedscompat$savedHurtTime = dragon.hurtTime;
        ayamedscompat$savedDeathTime = dragon.deathTime;
        if (AyamePaperDoll.CONFIGS.dragonHurtFlash.getValue()) {
            dragon.hurtTime = player.hurtTime;
        }
        // Vanilla death tilt is applied from the rendered entity's deathTime; sync the proxy.
        if (AyamePaperDoll.CONFIGS.dragonDeathAnimation.getValue()) {
            dragon.deathTime = player.deathTime;
        }
        ayamedscompat$syncedDragon = dragon;
    }

    @Inject(method = "renderDragon", at = @At("RETURN"), remap = false)
    private static void ayamedscompat$restoreHurtFlash(final RenderPlayerEvent.Pre event, final CallbackInfo ci) {
        if (ayamedscompat$syncedDragon != null) {
            ayamedscompat$syncedDragon.hurtTime = ayamedscompat$savedHurtTime;
            ayamedscompat$syncedDragon.deathTime = ayamedscompat$savedDeathTime;
            ayamedscompat$syncedDragon = null;
        }
    }
}
