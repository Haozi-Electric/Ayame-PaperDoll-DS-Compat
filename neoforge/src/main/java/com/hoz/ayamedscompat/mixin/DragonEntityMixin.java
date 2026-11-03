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

import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import com.hoz.ayamedscompat.shared.AyameDSCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DS 2.0.72 only plays dragon animations while the game is paused for inventory/UI renders.
 * The paper doll is a UI render, so keep its animations advancing like the old behaviour.
 */
@Mixin(value = DragonEntity.class, remap = false)
public abstract class DragonEntityMixin {

    @Inject(method = "shouldPlayAnimsWhileGamePaused", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void ayamedscompat$playAnimsWhilePaused(final CallbackInfoReturnable<Boolean> cir) {
        if (AyameDSCompat.isRenderingForCompat()) {
            cir.setReturnValue(true);
        }
    }
}
