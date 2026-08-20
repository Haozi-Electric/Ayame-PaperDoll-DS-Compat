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

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hooks between the common PaperDollRenderer and the neoforge DragonSurvival layer.
 * The common module never depends on DS; the neoforge module registers providers here.
 */
public final class AyameDSCompat {
    private static int compatRenderingDepth;
    private static boolean compatTargetDragon;
    private static DragonScaleProvider dragonScaleProvider;
    private static DragonUIRenderStateProvider dragonUIRenderStateProvider;

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

    /** Whether the paper doll is currently rendering a dragon player (DS compat active). */
    public static boolean isCompatTargetDragon() {
        return compatTargetDragon;
    }

    public static void setCompatTargetDragon(boolean dragon) {
        compatTargetDragon = dragon;
    }

    public static void setDragonScaleProvider(DragonScaleProvider provider) {
        dragonScaleProvider = provider;
    }

    /** Scale factor (1.0 = unchanged) to apply to the paper doll model of {@code entity}. */
    public static float getDragonScaleFactor(Entity entity) {
        return dragonScaleProvider == null ? 1.0f : dragonScaleProvider.getDragonScaleFactor(entity);
    }

    public static void setDragonUIRenderStateProvider(DragonUIRenderStateProvider provider) {
        dragonUIRenderStateProvider = provider;
    }

    /**
     * Returns a DragonSurvival dragon render state for the paper doll, or {@code null}
     * when the entity is not a dragon player. bodyYaw/headYaw/headPitch come from the
     * paper doll's locked pose; autoScale normalizes an oversized dragon back to human
     * height (disabled when the config toggle is off).
     */
    public static EntityRenderState createDragonUIRenderState(LivingEntity entity, float partialTick,
                                                              double bodyYaw, double headYaw, double headPitch,
                                                              boolean autoScale) {
        return dragonUIRenderStateProvider == null
                ? null
                : dragonUIRenderStateProvider.createDragonUIRenderState(entity, partialTick, bodyYaw, headYaw, headPitch, autoScale);
    }

    @FunctionalInterface
    public interface DragonScaleProvider {
        float getDragonScaleFactor(Entity entity);
    }

    @FunctionalInterface
    public interface DragonUIRenderStateProvider {
        EntityRenderState createDragonUIRenderState(LivingEntity entity, float partialTick,
                                                    double bodyYaw, double headYaw, double headPitch,
                                                    boolean autoScale);
    }
}
