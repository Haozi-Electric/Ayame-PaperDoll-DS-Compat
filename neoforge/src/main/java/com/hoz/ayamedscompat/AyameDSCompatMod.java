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
import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import com.hoz.ayamedscompat.mixin.ClientDragonRendererAccessor;
import com.hoz.ayamedscompat.mixin.DragonRenderDataAccessor;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.common.handlers.DragonSizeHandler;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MovementData;
import by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;

/**
 * NeoForge mod registration shell. The compat logic lives in {@link AyameDSCompat} (common);
 * this class registers the provider that extracts the live dragon render state for the paper
 * doll, so it keeps the real animation instead of the static UI preview.
 */
@Mod(value = AyameDSCompatMod.MOD_ID, dist = Dist.CLIENT)
public final class AyameDSCompatMod {
    public static final String MOD_ID = "ayame_ds_compat";

    public AyameDSCompatMod() {
        AyameDSCompat.setDragonQuery(DragonStateProvider::isDragon);
        AyameDSCompat.setCarriedPassengerQuery(CarryOnCompat::isCarriedPlayer);
        AyameDSCompat.setCompatRenderPassEndHook(BoneCacheGuard::restoreAll);
        AyameDSCompat.setDragonUIRenderStateProvider((entity, partialTick, bodyYaw, headYaw, headPitch, autoScale, chainChild) -> {
            if (!(entity instanceof Player player) || !DragonStateProvider.isDragon(player)) {
                return null;
            }
            DragonEntity dragon = ClientDragonRenderer.getOrCreateDragon(player);
            if (dragon == null) {
                return null;
            }
            // A neck-locking emote freezes query.head_pitch to 0; the paper doll wants the head
            // to follow its own pitch, so unlock the neck for this extraction and restore after.
            boolean savedNeckLocked = dragon.neckLocked;
            dragon.neckLocked = false;
            MovementData movement = MovementData.getData(player);
            // DS already prepares the dragon during its world render. Preparing again here would
            // advance body/head angles (and glide tilt) twice per frame. Only do it when the world
            // render skipped this dragon: local player gliding in first person without
            // first-person-flight rendering.
            if (ayamedscompat$worldDidNotPrepareDragon(player)) {
                ClientDragonRenderer.setDragonMovementData(player, Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks());
                ClientDragonRendererAccessor.ds$handleFlightMovement(player, dragon, movement, partialTick);
            }
            ClientDragonRendererAccessor.ds$syncDragonRenderState(player, dragon);
            try {
                EntityRenderState renderState = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(dragon, partialTick);
                // Fix the body facing forward by overriding ONLY the render data's body yaw. This
                // is the rotation the renderer applies in setupRender; the head/neck animation still
                // reads the real MovementData, so it stays natural instead of welding.
                if (renderState instanceof GeoRenderState geo) {
                    DragonRenderer.DragonRenderData renderData = geo.getGeckolibData(DragonRenderer.DRAGON_RENDER_DATA);
                    if (renderData != null) {
                        // DragonRenderData is final, so cast through Object for the mixin accessor.
                        DragonRenderDataAccessor accessor = (DragonRenderDataAccessor) (Object) renderData;
                        accessor.ds$setBodyYaw(bodyYaw);
                        // DS's setupRender also checks prevXRot/prevZRot for the glide pose. Zero
                        // them when wobble is disabled, and for chain children: their seat already
                        // inherits the mount's bone tilt, so their own mirrored tilt would double.
                        if (chainChild || !AyamePaperDoll.CONFIGS.dsGlideWobble.getValue()) {
                            accessor.ds$setPrevXRot(0);
                            accessor.ds$setPrevZRot(0);
                        }
                    }
                }
                if (autoScale && renderState instanceof LivingEntityRenderState living) {
                    // Same normalization as DS's InventoryScreen: reset the SCALE-driven growth
                    // so an oversized dragon renders at its base (human-ish) height.
                    DragonStateHandler handler = DragonStateProvider.getData(player);
                    float scale = (float) player.getAttribute(Attributes.SCALE).getValue();
                    EntityDimensions dimensions = DragonSizeHandler.calculateDimensions(handler, player, handler.previousPose);
                    living.boundingBoxWidth = dimensions.width() / scale;
                    living.boundingBoxHeight = dimensions.height() / scale;
                    living.scale = 1.0F;
                }
                return renderState;
            } catch (Exception e) {
                return null;
            } finally {
                dragon.neckLocked = savedNeckLocked;
            }
        });
    }

    /** True when the world render will not have prepared the dragon this frame. */
    private static boolean ayamedscompat$worldDidNotPrepareDragon(final Player player) {
        return player == Minecraft.getInstance().player
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && ServerFlightHandler.isGliding(player)
                && !ClientDragonRenderer.renderFirstPersonFlight;
    }
}
