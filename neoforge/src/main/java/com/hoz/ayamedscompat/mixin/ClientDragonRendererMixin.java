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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DS renders a client-side DragonEntity proxy, so the player's hurtTime/deathTime never reach
 * the extracted render state. Sync the proxy for the duration of the world render; GeckoLib
 * copies hurtTime/deathTime into the render state during extraction.
 */
@Mixin(value = ClientDragonRenderer.class, remap = false)
public abstract class ClientDragonRendererMixin {

    @Unique
    private static DragonEntity ayamedscompat$syncedDragon;
    @Unique
    private static int ayamedscompat$savedHurtTime;
    @Unique
    private static int ayamedscompat$savedDeathTime;

    @Inject(method = "renderDragon", at = @At("HEAD"), remap = false)
    private static void ayamedscompat$syncHurtFlash(final RenderPlayerEvent.Pre event, final CallbackInfo ci) {
        ayamedscompat$syncedDragon = null;
        if (!(event.getRenderState() instanceof AvatarRenderState renderState)
                || Minecraft.getInstance().level == null
                || !(Minecraft.getInstance().level.getEntity(renderState.id) instanceof Player player)
                || !DragonStateProvider.isDragon(player)) {
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
