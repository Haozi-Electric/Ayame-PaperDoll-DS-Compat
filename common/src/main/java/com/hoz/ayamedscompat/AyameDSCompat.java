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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hooks between the common PaperDollRenderer and the neoforge DragonSurvival layer.
 * The common module never depends on DS; the neoforge module registers providers here.
 */
public final class AyameDSCompat {
    private static int compatRenderingDepth;
    private static boolean compatTargetDragon;
    private static DragonScaleProvider dragonScaleProvider;
    private static DragonUIRenderStateProvider dragonUIRenderStateProvider;
    private static DragonQuery dragonQuery;
    private static CarriedPassengerQuery carriedPassengerQuery;
    private static Runnable compatRenderPassEndHook;
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

    public static void setCompatRenderPassEndHook(Runnable hook) {
        compatRenderPassEndHook = hook;
    }

    /** Called by the paper doll renderer after each forced feature pass. */
    public static void afterCompatRenderPass() {
        if (compatRenderPassEndHook != null) {
            compatRenderPassEndHook.run();
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
                                                              boolean autoScale, boolean chainChild) {
        return dragonUIRenderStateProvider == null
                ? null
                : dragonUIRenderStateProvider.createDragonUIRenderState(entity, partialTick, bodyYaw, headYaw, headPitch, autoScale, chainChild);
    }

    public static void setDragonQuery(DragonQuery query) {
        dragonQuery = query;
    }

    /** True when the entity is a DragonSurvival dragon (a player with dragon state). */
    public static boolean isDragon(Entity entity) {
        return dragonQuery != null && dragonQuery.isDragon(entity);
    }

    @FunctionalInterface
    public interface DragonQuery {
        boolean isDragon(Entity entity);
    }

    public static void setCarriedPassengerQuery(CarriedPassengerQuery query) {
        carriedPassengerQuery = query;
    }

    /** True when CarryOn is holding {@code passenger}; its anchor bypasses the MountingBone. */
    public static boolean isCarriedPassenger(Entity carrier, Entity passenger) {
        return carriedPassengerQuery != null && carriedPassengerQuery.isCarriedPassenger(carrier, passenger);
    }

    @FunctionalInterface
    public interface CarriedPassengerQuery {
        boolean isCarriedPassenger(Entity carrier, Entity passenger);
    }

    /** Starts collecting the next MountingBone transform relative to {@code nodeBase}. */
    public static void beginSeatCapture(Matrix4f nodeBase) {
        seatCaptureBaseInverse = new Matrix4f(nodeBase).invert();
        seatCaptureBonePose = null;
        seatCaptureActive = true;
        seatCaptured = false;
    }

    public static boolean isSeatCaptureActive() {
        return seatCaptureActive;
    }

    public static void captureSeatMatrix(Matrix4f bonePose) {
        if (seatCaptureActive && !seatCaptured) {
            seatCaptureBonePose = new Matrix4f(bonePose);
            seatCaptured = true;
        }
    }

    /**
     * Returns the MountingBone position relative to the captured render base and the bone's
     * up-axis tilt. The tilt is exactly what DS uses in its renderer (calculateBoneTilt):
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
        seatCaptureBaseInverse = null;
        seatCaptureBonePose = null;
        seatCaptured = false;
    }

    private static boolean isFinite(Vector3f vector) {
        return Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z);
    }

    private static boolean isFinite(Quaternionf quaternion) {
        return Float.isFinite(quaternion.x) && Float.isFinite(quaternion.y)
                && Float.isFinite(quaternion.z) && Float.isFinite(quaternion.w);
    }

    public record SeatTransform(Vector3f position, Quaternionf tilt) {}

    @FunctionalInterface
    public interface DragonScaleProvider {
        float getDragonScaleFactor(Entity entity);
    }

    @FunctionalInterface
    public interface DragonUIRenderStateProvider {
        EntityRenderState createDragonUIRenderState(LivingEntity entity, float partialTick,
                                                    double bodyYaw, double headYaw, double headPitch,
                                                    boolean autoScale, boolean chainChild);
    }
}
