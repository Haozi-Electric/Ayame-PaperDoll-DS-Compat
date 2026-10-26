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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared state between the common paper doll renderer and the NeoForge DS mixins. */
public final class AyameDSCompat {
    private static int compatRenderingDepth;
    private static DragonScaleProvider dragonScaleProvider;
    private static DragonQuery dragonQuery;
    private static CarriedPassengerQuery carriedPassengerQuery;
    private static Entity seatCaptureTarget;
    private static Matrix4f seatCaptureBaseInverse;
    private static Matrix4f seatCaptureBonePose;
    private static boolean seatCaptureActive;
    private static boolean seatCaptured;

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

    /** Registers the NeoForge-side scale provider so common stays DS-free. */
    public static void setDragonScaleProvider(DragonScaleProvider provider) {
        dragonScaleProvider = provider;
    }

    /** Scale factor (1.0 = unchanged) for the paper doll model of {@code entity}. */
    public static float getDragonScaleFactor(Entity entity) {
        return dragonScaleProvider == null ? 1.0f : dragonScaleProvider.getDragonScaleFactor(entity);
    }

    @FunctionalInterface
    public interface DragonScaleProvider {
        float getDragonScaleFactor(Entity entity);
    }

    public static void setDragonQuery(final DragonQuery query) {
        dragonQuery = query;
    }

    /** True when the entity is a DragonSurvival dragon (a player with dragon state). */
    public static boolean isDragon(final Entity entity) {
        return dragonQuery != null && dragonQuery.isDragon(entity);
    }

    @FunctionalInterface
    public interface DragonQuery {
        boolean isDragon(Entity entity);
    }

    public static void setCarriedPassengerQuery(final CarriedPassengerQuery query) {
        carriedPassengerQuery = query;
    }

    /** True when CarryOn is holding {@code passenger}; its anchor bypasses the MountingBone. */
    public static boolean isCarriedPassenger(final Entity carrier, final Entity passenger) {
        return carriedPassengerQuery != null && carriedPassengerQuery.isCarriedPassenger(carrier, passenger);
    }

    @FunctionalInterface
    public interface CarriedPassengerQuery {
        boolean isCarriedPassenger(Entity carrier, Entity passenger);
    }

    /** Starts collecting the MountingBone transform relative to {@code nodeBase}. */
    public static void beginSeatCapture(final Entity mount, final Matrix4f nodeBase) {
        seatCaptureTarget = mount;
        seatCaptureBaseInverse = new Matrix4f(nodeBase).invert();
        seatCaptureBonePose = null;
        seatCaptureActive = true;
        seatCaptured = false;
    }

    public static boolean isSeatCaptureActive() {
        return seatCaptureActive;
    }

    public static boolean isSeatCaptureTarget(final Entity player) {
        return seatCaptureActive && player == seatCaptureTarget;
    }

    public static void captureSeatMatrix(final Matrix4f bonePose) {
        if (seatCaptureActive && !seatCaptured) {
            seatCaptureBonePose = new Matrix4f(bonePose);
            seatCaptured = true;
        }
    }

    /**
     * Returns the MountingBone position relative to the captured render base and the bone's
     * up-axis tilt. The tilt is exactly what DS uses in PlayerRendererMixin (calculateBoneTilt):
     * it maps +Y to the bone's up direction and contains no yaw or scale.
     */
    public static SeatTransform takeSeatTransform() {
        if (!seatCaptureActive || !seatCaptured) {
            return null;
        }

        Matrix4f local = new Matrix4f(seatCaptureBaseInverse).mul(seatCaptureBonePose);
        Vector3f position = local.getTranslation(new Vector3f());
        if (!isFinite(position)) {
            return null;
        }

        Vector3f up = local.transformDirection(new Vector3f(0, 1, 0));
        float lengthSquared = up.lengthSquared();
        Quaternionf tilt = new Quaternionf();
        if (Float.isFinite(lengthSquared) && lengthSquared >= 1.0E-8F) {
            up.div((float) Math.sqrt(lengthSquared));
            tilt.rotationTo(0, 1, 0, up.x, up.y, up.z);
            if (!isFinite(tilt)) {
                tilt.identity();
            } else {
                tilt.normalize();
            }
        }

        return new SeatTransform(position, tilt);
    }

    public static void endSeatCapture() {
        seatCaptureActive = false;
        seatCaptureTarget = null;
        seatCaptureBaseInverse = null;
        seatCaptureBonePose = null;
        seatCaptured = false;
    }

    private static boolean isFinite(final Vector3f vector) {
        return Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z);
    }

    private static boolean isFinite(final Quaternionf quaternion) {
        return Float.isFinite(quaternion.x) && Float.isFinite(quaternion.y)
                && Float.isFinite(quaternion.z) && Float.isFinite(quaternion.w);
    }

    public record SeatTransform(Vector3f position, Quaternionf tilt) {}
}
