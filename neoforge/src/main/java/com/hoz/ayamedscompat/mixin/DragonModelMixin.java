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

import by.dragonsurvivalteam.dragonsurvival.client.models.DragonModel;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import com.hoz.ayamedscompat.AyameDSCompat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animation.AnimationState;

/**
 * Anchors the head/neck UP-DOWN (pitch) freedom to the paper doll's pose at the
 * point where DS reads MovementData into the Molang queries that drive the head
 * bones. The yaw (left-right) values are pinned to the paper doll's pose so DS's
 * natural yaw handling can't introduce the glide-time right bias / tail-yaw
 * dividing-line flip, but this means no left-right freedom (as the user chose).
 * All LastFrame values are pinned so the *_change handlers don't twitch.
 * deltaMovement is untouched so the tail keeps its up-down motion.
 */
@Mixin(value = DragonModel.class, remap = false)
public abstract class DragonModelMixin {

    @Inject(method = "applyMolangQueries", at = @At("HEAD"), remap = false)
    private void ayamedscompat$overridePose(final AnimationState<DragonEntity> animationState, final double currentTick,
                                            final CallbackInfo ci) {
        if (!AyameDSCompat.isRenderingForCompat()) {
            return;
        }

        DragonEntity dragon = animationState.getAnimatable();
        Player player = dragon.getPlayer();

        if (player == null) {
            return;
        }

        MovementData movement = MovementData.getData(player);
        movement.bodyYaw = player.yBodyRot;
        movement.bodyYawLastFrame = movement.bodyYaw;
        // headYaw pinned to 0 (head aligned with the body, no left-right freedom) so the
        // angleDifference between the paper doll's yHeadRot/yBodyRot doesn't get amplified
        // by the HeadTurnHandler into a right bias during glide.
        movement.headYaw = 0;
        movement.headYawLastFrame = 0;
        movement.headPitch = player.getXRot();
        movement.headPitchLastFrame = movement.headPitch;
    }
}
