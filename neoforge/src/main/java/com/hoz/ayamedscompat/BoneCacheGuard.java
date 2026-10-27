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

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import com.hoz.ayamedscompat.mixin.DragonRendererAccessor;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The paper doll renders a live dragon render state, which makes DS write its shared
 * MountingBone/seat caches from HUD space. Snapshot the entries before the deferred render
 * and restore them afterwards so world seats and the riding camera are not polluted.
 */
public final class BoneCacheGuard {
    private static final Map<Integer, SavedEntry> SAVED = new LinkedHashMap<>();

    private BoneCacheGuard() {}

    public static void begin(int dragonId) {
        if (SAVED.containsKey(dragonId)) {
            return;
        }

        SavedEntry entry = new SavedEntry(
                copy(DragonRenderer.BONE_POSITIONS.get(dragonId)),
                copy(DragonRendererAccessor.ds$getBoneOffsets().get(dragonId)),
                copy(DragonRendererAccessor.ds$getBoneRotations().get(dragonId)),
                copy(DragonRendererAccessor.ds$getBoneUpdateTicks().get(dragonId))
        );
        SAVED.put(dragonId, entry);
    }

    public static void restoreAll() {
        if (SAVED.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, SavedEntry> entry : SAVED.entrySet()) {
            int dragonId = entry.getKey();
            SavedEntry saved = entry.getValue();
            restore(DragonRenderer.BONE_POSITIONS, dragonId, saved.positions());
            restore(DragonRendererAccessor.ds$getBoneOffsets(), dragonId, saved.offsets());
            restore(DragonRendererAccessor.ds$getBoneRotations(), dragonId, saved.rotations());
            restore(DragonRendererAccessor.ds$getBoneUpdateTicks(), dragonId, saved.updateTicks());
        }
        SAVED.clear();
    }

    private static <T> Map<String, T> copy(Map<String, T> map) {
        return map == null ? null : new HashMap<>(map);
    }

    private static <T> void restore(Map<Integer, Map<String, T>> cache, int dragonId, Map<String, T> saved) {
        if (saved == null) {
            cache.remove(dragonId);
        } else {
            cache.put(dragonId, saved);
        }
    }

    private record SavedEntry(Map<String, Vec3> positions,
                              Map<String, Vec3> offsets,
                              Map<String, Quaternionf> rotations,
                              Map<String, Long> updateTicks) {}
}
