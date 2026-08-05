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
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler;
import com.hoz.ayamedscompat.AyameDSCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientDragonRenderer.class, remap = false)
public abstract class ClientDragonRendererMixin {

    @Unique
    private static double ayamedscompat$savedHeadYaw;
    @Unique
    private static double ayamedscompat$savedHeadPitch;
    @Unique
    private static double ayamedscompat$savedBodyYaw;
    @Unique
    private static double ayamedscompat$savedHeadYawLastFrame;
    @Unique
    private static double ayamedscompat$savedHeadPitchLastFrame;
    @Unique
    private static double ayamedscompat$savedBodyYawLastFrame;
    @Unique
    private static Vec3 ayamedscompat$savedDeltaMovement;
    @Unique
    private static Vec3 ayamedscompat$savedDeltaMovementLastFrame;
    @Unique
    private static boolean ayamedscompat$movementSaved;

    // DS's setDragonMovementData is left running during the paper doll so the
    // head/neck/tail animation keeps getting real data (previously we cancelled
    // it, which froze them at the pre-glide pose). We only save/restore the
    // movement data around the render so the real-world values aren't corrupted.
    @Inject(method = "renderDragon", at = @At("HEAD"), remap = false)
    private static void ayamedscompat$saveMovement(final RenderPlayerEvent.Pre event, final CallbackInfo ci) {
        if (!AyameDSCompat.isRenderingForCompat() || ayamedscompat$movementSaved) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        MovementData movement = MovementData.getData(player);
        ayamedscompat$savedHeadYaw = movement.headYaw;
        ayamedscompat$savedHeadPitch = movement.headPitch;
        ayamedscompat$savedBodyYaw = movement.bodyYaw;
        ayamedscompat$savedHeadYawLastFrame = movement.headYawLastFrame;
        ayamedscompat$savedHeadPitchLastFrame = movement.headPitchLastFrame;
        ayamedscompat$savedBodyYawLastFrame = movement.bodyYawLastFrame;
        ayamedscompat$savedDeltaMovement = movement.deltaMovement;
        ayamedscompat$savedDeltaMovementLastFrame = movement.deltaMovementLastFrame;
        ayamedscompat$movementSaved = true;
    }

    @Inject(method = "renderDragon", at = @At("RETURN"), remap = false)
    private static void ayamedscompat$restoreMovement(final RenderPlayerEvent.Pre event, final CallbackInfo ci) {
        if (!ayamedscompat$movementSaved) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            ayamedscompat$movementSaved = false;
            return;
        }
        MovementData movement = MovementData.getData(player);
        movement.headYaw = ayamedscompat$savedHeadYaw;
        movement.headPitch = ayamedscompat$savedHeadPitch;
        movement.bodyYaw = ayamedscompat$savedBodyYaw;
        movement.headYawLastFrame = ayamedscompat$savedHeadYawLastFrame;
        movement.headPitchLastFrame = ayamedscompat$savedHeadPitchLastFrame;
        movement.bodyYawLastFrame = ayamedscompat$savedBodyYawLastFrame;
        movement.deltaMovement = ayamedscompat$savedDeltaMovement;
        movement.deltaMovementLastFrame = ayamedscompat$savedDeltaMovementLastFrame;
        ayamedscompat$movementSaved = false;
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

    @WrapOperation(method = "handleFlightMovement", at = @At(value = "INVOKE",
            target = "Lby/dragonsurvivalteam/dragonsurvival/server/handlers/ServerFlightHandler;"
                   + "isGliding(Lnet/minecraft/world/entity/player/Player;)Z"),
            remap = false, require = 0)
    private static boolean ayamedscompat$forceRenderBodyInFlightMovement(final Player player,
                                                                         final Operation<Boolean> original) {
        if (AyameDSCompat.isRenderingForCompat()) {
            return false;
        }
        return original.call(player);
    }
}
