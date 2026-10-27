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

import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Installs a GeckoLib bone updater that captures the MountingBone's tracked pose.
 * The updater runs while the deferred model render task executes, which is the only time
 * bone matrices exist. The tracked pose stops before translateAwayFromPivotPoint, exactly
 * like GeckoLib's bone position listeners.
 */
public final class SeatCapture {
    // Literal: the compile-time DS jar does not expose DragonRidingHandler.MOUNTING_BONE.
    private static final String MOUNTING_BONE = "MountingBone";

    private SeatCapture() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void install(RenderPassInfo<?> renderPassInfo) {
        RenderPassInfo raw = renderPassInfo;
        raw.addBoneUpdater((RenderPassInfo.BoneUpdater) (passInfo, snapshots) ->
                snapshots.get(MOUNTING_BONE).ifPresent(snapshot -> {
                    PoseStack bonePose = new PoseStack();
                    bonePose.last().pose().set(passInfo.getModelRenderMatrixState());
                    RenderUtil.transformToBone(bonePose, snapshot.getBone());
                    AyameDSCompat.captureSeatMatrix(bonePose.last().pose());
                }));
    }
}
