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

import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads DS 2.0.72's tick-interpolated animation state without a compile-time dependency on it,
 * so the mod still loads with older DragonSurvival versions. Returns null when the running DS
 * does not expose the new state (the caller then keeps the legacy per-tick values).
 */
public final class DragonAnimationSampler {
    private static final Field ANIMATION_STATE;
    private static final Method SAMPLE;
    private static final Method FLIGHT_PITCH;
    private static final Method FLIGHT_ROLL;
    private static final Method BODY_YAW;

    static {
        Field state = null;
        Method sample = null;
        Method pitch = null;
        Method roll = null;
        Method bodyYaw = null;
        try {
            state = DragonEntity.class.getDeclaredField("animationState");
            state.setAccessible(true);
            final Class<?> stateClass = state.getType();
            sample = stateClass.getMethod("sample", float.class);
            final Class<?> poseClass = sample.getReturnType();
            pitch = poseClass.getMethod("flightPitch");
            roll = poseClass.getMethod("flightRoll");
            bodyYaw = poseClass.getMethod("bodyYaw");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            state = null;
            sample = null;
            pitch = null;
            roll = null;
            bodyYaw = null;
        }
        ANIMATION_STATE = state;
        SAMPLE = sample;
        FLIGHT_PITCH = pitch;
        FLIGHT_ROLL = roll;
        BODY_YAW = bodyYaw;
    }

    private DragonAnimationSampler() {}

    public static boolean isAvailable() {
        return SAMPLE != null;
    }

    /** Returns {flightPitch, flightRoll} interpolated for {@code partialTick}, or null. */
    public static float[] sampleFlight(final DragonEntity dragon, final float partialTick) {
        final Object pose = sample(dragon, partialTick);
        if (pose == null) {
            return null;
        }
        try {
            return new float[]{
                    ((Number) FLIGHT_PITCH.invoke(pose)).floatValue(),
                    ((Number) FLIGHT_ROLL.invoke(pose)).floatValue()
            };
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /** Returns the interpolated body yaw for {@code partialTick}, or null. */
    public static Double sampleBodyYaw(final DragonEntity dragon, final float partialTick) {
        final Object pose = sample(dragon, partialTick);
        if (pose == null) {
            return null;
        }
        try {
            return ((Number) BODY_YAW.invoke(pose)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object sample(final DragonEntity dragon, final float partialTick) {
        if (SAMPLE == null || dragon == null) {
            return null;
        }
        try {
            final Object state = ANIMATION_STATE.get(dragon);
            return state == null ? null : SAMPLE.invoke(state, partialTick);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
