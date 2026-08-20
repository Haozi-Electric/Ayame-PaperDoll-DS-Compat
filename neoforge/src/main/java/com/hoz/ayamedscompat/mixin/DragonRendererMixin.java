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
import com.hoz.ayamedscompat.AyameDSCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the world, DragonRenderer offsets the model from the entity by an amount derived from
 * the live MovementData bodyYaw and the dragon's size. In the paper doll that live offset
 * makes the model orbit the preview origin when the view rotates, so we zero it during compat
 * rendering and let the paper doll's own translation center the model.
 */
@Mixin(value = DragonRenderer.class, remap = false)
public abstract class DragonRendererMixin {

    @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private void ayamedscompat$centerModel(DragonRenderer.DragonRenderData renderData,
                                           CallbackInfoReturnable<Vec3> cir) {
        if (AyameDSCompat.isRenderingForCompat()) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }

    // During compat, suppress the gliding pitch/roll applied in setupRender so the paper doll
    // dragon body stays facing forward instead of wobbling side to side (config ds_glide_forward).
    @WrapOperation(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lby/dragonsurvivalteam/dragonsurvival/server/handlers/ServerFlightHandler;"
                   + "isGliding(Lnet/minecraft/world/entity/player/Player;)Z"),
            remap = false)
    private boolean ayamedscompat$forceRenderBodyInSetupRender(final Player player,
                                                               final Operation<Boolean> original) {
        if (AyameDSCompat.isRenderingForCompat() && AyamePaperDoll.CONFIGS.dsGlideForward.getValue()) {
            return false;
        }
        return original.call(player);
    }
}
