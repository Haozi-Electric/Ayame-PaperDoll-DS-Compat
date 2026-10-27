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

import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Exposes the private bone caches so the paper doll render can restore them afterwards. */
@Mixin(value = DragonRenderer.class, remap = false)
public interface DragonRendererAccessor {

    @Accessor("BONE_OFFSETS")
    static Map<Integer, Map<String, Vec3>> ds$getBoneOffsets() {
        throw new AssertionError();
    }

    @Accessor("BONE_ROTATIONS")
    static Map<Integer, Map<String, Quaternionf>> ds$getBoneRotations() {
        throw new AssertionError();
    }

    @Accessor("BONE_UPDATE_TICKS")
    static Map<Integer, Map<String, Long>> ds$getBoneUpdateTicks() {
        throw new AssertionError();
    }
}
