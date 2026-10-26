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

package com.hoz.ayamedscompat;

import by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge mod registration shell.
 * The actual compat logic lives in {@link AyameDSCompat} (common module)
 * and the DS mixins in {@code com.hoz.ayamedscompat.mixin}.
 */
@Mod(value = AyameDSCompatMod.MOD_ID, dist = Dist.CLIENT)
public final class AyameDSCompatMod {
    public static final String MOD_ID = "ayame_ds_compat";

    public AyameDSCompatMod() {
        // The paper doll's common renderer asks via AyameDSCompat; the provider resolves the
        // dragon's current visual scale (player SCALE attribute, human = 1.0) and normalizes it
        // back to human height so oversized dragons don't overflow the paper doll. Non-dragons
        // and players without a dragon entity yet are left untouched (factor 1.0).
        AyameDSCompat.setDragonQuery(DragonStateProvider::isDragon);
        AyameDSCompat.setCarriedPassengerQuery(CarryOnCompat::isCarriedPlayer);

        AyameDSCompat.setDragonScaleProvider(entity -> {
            if (!(entity instanceof Player player) || !DragonStateProvider.isDragon(player)) {
                return 1.0f;
            }
            DragonEntity dragon = ClientDragonRenderer.getDragon(player);
            if (dragon == null) {
                return 1.0f;
            }
            float scale = dragon.getScale();
            return scale > 0.001f ? 1.0f / scale : 1.0f;
        });
    }
}
