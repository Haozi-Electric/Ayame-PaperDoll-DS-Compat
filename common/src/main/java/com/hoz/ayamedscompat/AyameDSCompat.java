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

import net.minecraft.world.entity.Entity;

/**
 * Static depth counter shared across common and neoforge modules.
 * PaperDollRenderer (in common) calls push/pop around entity render;
 * the DS mixins (in neoforge) query {@link #isRenderingForCompat()}
 * to decide whether to apply compatibility fixes.
 */
public final class AyameDSCompat {
    private static int compatRenderingDepth;
    private static DragonScaleProvider dragonScaleProvider;

    private AyameDSCompat() {}

    public static boolean isRenderingForCompat() {
        return compatRenderingDepth > 0;
    }

    public static void pushCompatRendering() {
        compatRenderingDepth++;
    }

    public static void popCompatRendering() {
        if (compatRenderingDepth > 0) {
            compatRenderingDepth--;
        }
    }

    /**
     * Registers the neoforge-side provider that computes how much a dragon's
     * paper doll model must be scaled to reach normal human height. Kept as a
     * hook so the common PaperDollRenderer never needs a DragonSurvival dependency.
     */
    public static void setDragonScaleProvider(DragonScaleProvider provider) {
        dragonScaleProvider = provider;
    }

    /** Scale factor (1.0 = unchanged) to apply to the paper doll model of {@code entity}. */
    public static float getDragonScaleFactor(Entity entity) {
        return dragonScaleProvider == null ? 1.0f : dragonScaleProvider.getDragonScaleFactor(entity);
    }

    @FunctionalInterface
    public interface DragonScaleProvider {
        float getDragonScaleFactor(Entity entity);
    }
}
