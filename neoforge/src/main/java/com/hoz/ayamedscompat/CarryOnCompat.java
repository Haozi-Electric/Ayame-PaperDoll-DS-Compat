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
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/** Reflective CarryOn bridge; returns false when CarryOn is absent. */
public final class CarryOnCompat {
    private static final String DATA_MANAGER = "tschipp.carryon.common.carry.CarryOnDataManager";
    private static final String DATA = "tschipp.carryon.common.carry.CarryOnData";
    private static final String CARRY_TYPE = "tschipp.carryon.common.carry.CarryOnData$CarryType";

    private static boolean resolved;
    private static Method getCarryData;
    private static Method isCarrying;
    private static Object playerCarryType;

    private CarryOnCompat() {}

    public static boolean isCarryingPlayer(final Entity carrier) {
        if (!(carrier instanceof Player)) {
            return false;
        }

        resolve();
        if (getCarryData == null || isCarrying == null || playerCarryType == null) {
            return false;
        }

        try {
            final Object data = getCarryData.invoke(null, carrier);
            return data != null && Boolean.TRUE.equals(isCarrying.invoke(data, playerCarryType));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isCarriedPlayer(final Entity carrier, final Entity passenger) {
        return passenger instanceof Player && isCarryingPlayer(carrier);
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;

        try {
            final Class<?> manager = Class.forName(DATA_MANAGER);
            final Class<?> data = Class.forName(DATA);
            final Class<?> carryType = Class.forName(CARRY_TYPE);
            getCarryData = manager.getMethod("getCarryData", Player.class);
            isCarrying = data.getMethod("isCarrying", carryType);
            playerCarryType = carryType.getField("PLAYER").get(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            getCarryData = null;
            isCarrying = null;
            playerCarryType = null;
        }
    }
}
