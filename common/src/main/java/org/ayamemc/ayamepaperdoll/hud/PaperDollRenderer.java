/*
 *     Highly configurable PaperDoll mod. Forked from Extra Player Renderer.
 *     Copyright (C) 2024-2025  LucunJi(Original author), HappyRespawnanchor
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

package org.ayamemc.ayamepaperdoll.hud;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.lwjgl.opengl.GL11;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import com.hoz.ayamedscompat.AyameDSCompat;
import com.hoz.ayamedscompat.AyameDSCompat.SeatTransform;
import org.ayamemc.ayamepaperdoll.AyamePaperDoll;
import org.ayamemc.ayamepaperdoll.config.Configs;
import org.ayamemc.ayamepaperdoll.config.Configs.RotationMode;
import org.ayamemc.ayamepaperdoll.hud.DataBackup.DataBackupEntry;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ayamemc.ayamepaperdoll.AyamePaperDoll.CONFIGS;

public class PaperDollRenderer {
    private static final List<DataBackupEntry<LivingEntity, ?>> LIVINGENTITY_BACKUP_ENTRIES = ImmutableList.of(
            new DataBackupEntry<>(LivingEntity::getPose, LivingEntity::setPose),
            // required for player on client side
            new DataBackupEntry<>(Entity::isCrouching, (e, flag) -> {
                if (e instanceof LocalPlayer player) player.crouching = flag;
            }),
            new DataBackupEntry<>(e -> e.swimAmount, (e, pitch) -> e.swimAmount = pitch),
            new DataBackupEntry<>(e -> e.swimAmountO, (e, pitch) -> e.swimAmountO = pitch),
            new DataBackupEntry<>(LivingEntity::isFallFlying, (e, flag) -> e.setSharedFlag(7, flag)),
            new DataBackupEntry<>(LivingEntity::getFallFlyingTicks, (e, ticks) -> e.fallFlyTicks = ticks),

            new DataBackupEntry<>(LivingEntity::getVehicle, (e, vehicle) -> e.vehicle = vehicle),

            new DataBackupEntry<>(e -> e.yBodyRotO, (e, yaw) -> e.yBodyRotO = yaw),
            new DataBackupEntry<>(e -> e.yBodyRot, (e, yaw) -> e.yBodyRot = yaw),
            new DataBackupEntry<>(e -> e.yHeadRotO, (e, yaw) -> e.yHeadRotO = yaw),
            new DataBackupEntry<>(e -> e.yHeadRot, (e, yaw) -> e.yHeadRot = yaw),
            new DataBackupEntry<>(e -> e.xRotO, (e, pitch) -> e.xRotO = pitch),
            new DataBackupEntry<>(LivingEntity::getXRot, LivingEntity::setXRot),

            new DataBackupEntry<>(e -> e.attackAnim, (e, prog) -> e.attackAnim = prog),
            new DataBackupEntry<>(e -> e.oAttackAnim, (e, prog) -> e.oAttackAnim = prog),
            new DataBackupEntry<>(e -> e.hurtTime, (e, time) -> e.hurtTime = time),
            new DataBackupEntry<>(LivingEntity::getRemainingFireTicks, LivingEntity::setRemainingFireTicks),
            new DataBackupEntry<>(e -> e.getSharedFlag(0), (e, flag) -> e.setSharedFlag(0, flag)) // on fire
    );
    private static final PaperDollRenderer instance = new PaperDollRenderer();
    private static long lastChainDebugLogNanos;
    private static long lastChainFitLogNanos;
    private final Minecraft minecraft = Minecraft.getInstance();

    private PaperDollRenderer() {
    }

    public static PaperDollRenderer getInstance() {
        return instance;
    }

    @SuppressWarnings("resource")
    private static int getLight(Entity entity, float tickDelta) {
        if (CONFIGS.useWorldLight.getValue()) {
            Level world = entity.level();
            int blockLight = world.getBrightness(LightLayer.BLOCK, BlockPos.containing(entity.getEyePosition(tickDelta)));
            int skyLight = world.getBrightness(LightLayer.SKY, BlockPos.containing(entity.getEyePosition(tickDelta)));
            int min = CONFIGS.worldLightMin.getValue();
            blockLight = Mth.clamp(blockLight, min, 15);
            skyLight = Mth.clamp(skyLight, min, 15);
            return LightTexture.pack(blockLight, skyLight);
        }
        return LightTexture.pack(15, 15);
    }

    private static float getFallFlyingLeaning(LivingEntity entity, float partialTicks) {
        float ticks = partialTicks + entity.getFallFlyingTicks();
        return Mth.clamp(ticks * ticks / 100f, 0f, 1f);
    }

    public static boolean shouldLockRotationYaw() {
        final RotationMode rotationUnlock = CONFIGS.rotationMode.getValue();
        return (rotationUnlock == RotationMode.LOCK);//|| (rotationUnlock == RotationMode.SMOOTH_LOCK);

    }

    // 这会导致织布机渲染问题，不要使用↓
    // follow convention in LayeredDrawer#renderInternal
    // guiGraphics.pose().translate(0, 0, 200);
    /**
     * Mimics the code in {@link InventoryScreen#renderEntityInInventory}
     */
    public void render(float partialTicks, GuiGraphics guiGraphics) {
        if (minecraft.level == null || minecraft.player == null || !CONFIGS.displayPaperDoll.getValue()) return;
        LivingEntity targetEntity = minecraft.level.players().stream().filter(p -> p.getName().getString().equals(CONFIGS.playerName.getValue())).findFirst().orElse(minecraft.player);
        if (CONFIGS.spectatorAutoSwitch.getValue() && minecraft.player.isSpectator()) {
            Entity cameraEntity = minecraft.getCameraEntity();
            if (cameraEntity instanceof LivingEntity livingEntity) {
                targetEntity = livingEntity;
            } else if (cameraEntity != null) {
                return;
            }
        }

        int scaledWidth = minecraft.getWindow().getGuiScaledWidth();
        int scaledHeight = minecraft.getWindow().getGuiScaledHeight();
        Configs.PoseOffsetMethod poseOffsetMethod = CONFIGS.poseOffsetMethod.getValue();

        List<LivingEntity> ridingChain = buildRidingChain(targetEntity, poseOffsetMethod);
        if (ridingChain != null) {
            renderRidingChain(ridingChain, targetEntity, poseOffsetMethod, partialTicks,
                    scaledWidth, scaledHeight);
            return;
        }

        var backup = new DataBackup<>(targetEntity, LIVINGENTITY_BACKUP_ENTRIES);
        backup.save();

        transformEntity(targetEntity, partialTicks, poseOffsetMethod == Configs.PoseOffsetMethod.FORCE_STANDING);

        DataBackup<LivingEntity> vehicleBackup = null;
        if (CONFIGS.renderVehicle.getValue() && poseOffsetMethod != Configs.PoseOffsetMethod.FORCE_STANDING && targetEntity.isPassenger()) {
            var vehicle = targetEntity.getVehicle();
            assert vehicle != null;

            // get the overall yaw before transforming
            var yawLerped = vehicle.getViewYRot(partialTicks);

            // FIXME: NEVERFIX - the rendered yaw of minecart is determined non-trivially in its MinecartEntityRenderer#render, so it cannot be fixed to 0 easily
            if (vehicle instanceof LivingEntity livingVehicle) {
                vehicleBackup = new DataBackup<>(livingVehicle, LIVINGENTITY_BACKUP_ENTRIES);
                vehicleBackup.save();
                transformEntity(livingVehicle, partialTicks, false);
            }

            performRendering(vehicle,
                    CONFIGS.offsetX.getValue() * scaledWidth,
                    CONFIGS.offsetY.getValue() * scaledHeight,
                    CONFIGS.size.getValue() * scaledHeight,
                    CONFIGS.mirrored.getValue(),
                    vehicle.getPosition(partialTicks).subtract(targetEntity.getPosition(partialTicks))
                            .yRot((float) Math.toRadians(yawLerped)).toVector3f(), // undo the rotation
                    CONFIGS.lightDegree.getValue(),
                    partialTicks, guiGraphics);
        }


        double targetPosY = CONFIGS.offsetY.getValue() * scaledHeight;
        float targetFitScale = 1.0f;
        Vector3f targetOffset = new Vector3f(0, (float) getPoseOffsetY(targetEntity, partialTicks, poseOffsetMethod), 0);
        if (poseOffsetMethod == Configs.PoseOffsetMethod.AUTO && AyameDSCompat.isDragon(targetEntity)) {
            targetFitScale = getSingleDragonFitScale(targetEntity);
        }
        performRendering(targetEntity,
                CONFIGS.offsetX.getValue() * scaledWidth,
                targetPosY,
                CONFIGS.size.getValue() * scaledHeight,
                CONFIGS.mirrored.getValue(),
                targetOffset,
                CONFIGS.lightDegree.getValue(),
                partialTicks, guiGraphics, targetFitScale);

        if (vehicleBackup != null) vehicleBackup.restore();

        backup.restore();
    }

    /** Returns the rendered riding chain, or null for the legacy target/vehicle path. */
    private List<LivingEntity> buildRidingChain(LivingEntity target, Configs.PoseOffsetMethod poseOffsetMethod) {
        if (poseOffsetMethod == Configs.PoseOffsetMethod.FORCE_STANDING) {
            return null;
        }

        final boolean includeVehicle = CONFIGS.renderVehicle.getValue() && target.isPassenger();
        final boolean includePassengers = CONFIGS.renderPassengers.getValue();
        if (!includeVehicle && !includePassengers) {
            return null;
        }

        List<LivingEntity> chain = new ArrayList<>();

        if (includeVehicle) {
            List<LivingEntity> ancestors = new ArrayList<>();
            for (Entity entity = target; entity != null; entity = entity.getVehicle()) {
                if (!(entity instanceof LivingEntity living)) {
                    return null; // non-living mounts keep the legacy path
                }
                ancestors.add(living);
            }
            Collections.reverse(ancestors);
            // Every mount needs a dragon seat anchor.
            for (int i = 0; i < ancestors.size() - 1; i++) {
                if (!AyameDSCompat.isDragon(ancestors.get(i))) {
                    return null;
                }
            }
            chain.addAll(ancestors);
        } else {
            chain.add(target);
        }

        if (includePassengers) {
            collectRenderedPassengers(target, chain);
        }

        return chain.size() > 1 ? chain : null;
    }

    private void collectRenderedPassengers(LivingEntity mount, List<LivingEntity> chain) {
        for (Entity passenger : mount.getPassengers()) {
            if (!(passenger instanceof LivingEntity living)) {
                continue;
            }
            // CarryOn carried players bypass the MountingBone with their own front-carry offset.
            if (!AyameDSCompat.isDragon(mount) && !AyameDSCompat.isCarriedPassenger(mount, living)) {
                continue;
            }
            chain.add(living);
            collectRenderedPassengers(living, chain);
        }
    }

    private void renderRidingChain(List<LivingEntity> chain, LivingEntity targetEntity,
                                   Configs.PoseOffsetMethod poseOffsetMethod, float partialTicks,
                                   int scaledWidth, int scaledHeight) {
        final LivingEntity root = chain.get(0);
        final RotationMode rotationMode = CONFIGS.rotationMode.getValue();
        final boolean mirror = CONFIGS.mirrored.getValue();
        final boolean needCullFix = needsCullFix(mirror);

        List<DataBackup<LivingEntity>> backups = new ArrayList<>(chain.size());
        for (LivingEntity entity : chain) {
            DataBackup<LivingEntity> backup = new DataBackup<>(entity, LIVINGENTITY_BACKUP_ENTRIES);
            backup.save();
            backups.add(backup);
        }

        EntityRenderDispatcher entityRenderDispatcher = minecraft.getEntityRenderDispatcher();

        try {
            for (LivingEntity entity : chain) {
                transformEntity(entity, partialTicks, false);
            }

            Map<LivingEntity, List<LivingEntity>> renderedChildren = buildRenderedChildren(chain);

            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.scale(1, 1, -1);
            modelViewStack.rotateY((float) Math.toRadians(CONFIGS.lightDegree.getValue() + 180));
            RenderSystem.applyModelViewMatrix();

            float size = (float) (CONFIGS.size.getValue() * scaledHeight);
            float anchorY = (float) (CONFIGS.offsetY.getValue() * scaledHeight);
            float groupScale = 1.0f;
            if (CONFIGS.autoScaleDragonToHumanHeight.getValue()) {
                groupScale = AyameDSCompat.getDragonScaleFactor(root);
            }

            float fitScale = getChainFitScale(chain, root);

            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(Axis.YP.rotationDegrees(-CONFIGS.lightDegree.getValue().floatValue() - 180.0F));
            poseStack.translate(CONFIGS.offsetX.getValue() * scaledWidth, anchorY, 0);
            if (mirror) {
                poseStack.scale(-1, 1, 1);
            }
            // Normalize the root and keep every other member at its world-relative size.
            poseStack.scale(size, size, size);
            if (groupScale != 1.0f) {
                poseStack.scale(groupScale, groupScale, groupScale);
            }
            if (fitScale != 1.0f) {
                poseStack.scale(fitScale, fitScale, fitScale);
                if (CONFIGS.debugMode.getValue()) {
                    long now = System.nanoTime();
                    if (now - lastChainFitLogNanos > 1_000_000_000L) {
                        lastChainFitLogNanos = now;
                        AyamePaperDoll.LOGGER.info("[Debug] auto-fit riding chain: nodes={}, fit={}",
                                chain.size(), fitScale);
                    }
                }
            }

            Quaternionf zRot = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf xyzRot = new Quaternionf().rotateXYZ(
                    (float) Math.toRadians(CONFIGS.rotationX.getValue()),
                    (float) Math.toRadians(CONFIGS.rotationY.getValue()),
                    (float) Math.toRadians(CONFIGS.rotationZ.getValue()));
            zRot.mul(xyzRot);
            poseStack.mulPose(zRot);

            Lighting.setupForEntityInInventory();
            xyzRot.conjugate();

            entityRenderDispatcher.overrideCameraOrientation(xyzRot);
            boolean renderHitbox = entityRenderDispatcher.shouldRenderHitBoxes();
            entityRenderDispatcher.setRenderHitBoxes(false);
            entityRenderDispatcher.setRenderShadow(false);
            MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

            if (needCullFix) {
                applyCullFix(mirror);
            }
            bufferSource.ayame_PaperDoll$setFlipCullFace(mirror);

            AyameDSCompat.pushCompatRendering();
            try {
                poseStack.pushPose();
                try {
                    if (root == targetEntity) {
                        double poseOffsetY = getPoseOffsetY(targetEntity, partialTicks, poseOffsetMethod);
                        if (poseOffsetY != 0) {
                            poseStack.translate(0, poseOffsetY, 0);
                        }
                    }
                    renderChainNode(poseStack, root, renderedChildren, rotationMode, partialTicks,
                            bufferSource, entityRenderDispatcher);
                } finally {
                    poseStack.popPose();
                }
            } finally {
                AyameDSCompat.popCompatRendering();
            }

            bufferSource.endBatch();
            bufferSource.ayame_PaperDoll$setFlipCullFace(false);

            if (needCullFix) {
                restoreCullFix();
            }

            entityRenderDispatcher.setRenderShadow(true);
            entityRenderDispatcher.setRenderHitBoxes(renderHitbox);

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
        } finally {
            for (int i = backups.size() - 1; i >= 0; i--) {
                backups.get(i).restore();
            }
        }
    }

    /** Scale-only fit for the riding chain; placement stays at the configured anchor. */
    private static float getChainFitScale(List<LivingEntity> chain, LivingEntity root) {
        if (!CONFIGS.autoFitRidingChain.getValue() || chain.size() <= 1) {
            return 1.0f;
        }
        return computeFitScale(getExpectedChainHeight(chain.size(), root));
    }

    /** Scale-only AUTO adaptation for a single dragon; the model never moves. */
    private static float getSingleDragonFitScale(LivingEntity dragon) {
        return computeFitScale(getExpectedModelHeight(dragon));
    }

    private static float computeFitScale(float modelHeight) {
        final double size = CONFIGS.size.getValue();
        final double anchor = CONFIGS.offsetY.getValue();
        // The doll grows upward from the anchor. Presets with the anchor below the screen can use
        // almost the whole screen height; top-corner presets only have the space above the anchor.
        final float available = anchor >= 1.0 ? 0.95f : (float) Math.max(0.06, Math.min(0.95, anchor - 0.05));
        final float needed = (float) (modelHeight * size);
        if (needed <= available) {
            return 1.0f;
        }
        return Math.max(0.15f, available / needed);
    }

    private static float getExpectedChainHeight(int nodes, LivingEntity root) {
        final float rootHeight = getExpectedModelHeight(root);
        return rootHeight + (nodes - 1) * Math.max(1.0f, rootHeight * 0.7f);
    }

    private static float getExpectedModelHeight(LivingEntity entity) {
        final boolean dragon = AyameDSCompat.isDragon(entity);
        if (dragon && CONFIGS.autoScaleDragonToHumanHeight.getValue()) {
            // The DS hitbox is about twice the rendered model; the pose ratio cancels the
            // constant factor and still follows pose changes.
            final float standing = entity.getDimensions(Pose.STANDING).height();
            final float current = entity.getDimensions(entity.getPose()).height();
            final float ratio = standing > 1.0E-4f ? current / standing : 1.0f;
            return 1.8f * Mth.clamp(ratio, 0.25f, 2.5f);
        }
        final float height = Math.max(1.0f, entity.getDimensions(entity.getPose()).height());
        return dragon ? height * 0.5f : height;
    }

    private Map<LivingEntity, List<LivingEntity>> buildRenderedChildren(List<LivingEntity> chain) {
        Set<LivingEntity> included = Collections.newSetFromMap(new IdentityHashMap<>());
        included.addAll(chain);
        Map<LivingEntity, List<LivingEntity>> children = new IdentityHashMap<>();
        for (LivingEntity entity : chain) {
            if (entity.getVehicle() instanceof LivingEntity mount && included.contains(mount)) {
                children.computeIfAbsent(mount, key -> new ArrayList<>(1)).add(entity);
            }
        }
        return children;
    }

    private void renderChainNode(PoseStack poseStack, LivingEntity node,
                                 Map<LivingEntity, List<LivingEntity>> renderedChildren,
                                 RotationMode rotationMode, float partialTicks,
                                 MultiBufferSource.BufferSource bufferSource,
                                 EntityRenderDispatcher entityRenderDispatcher) {
        List<LivingEntity> children = renderedChildren.get(node);
        if (children == null) {
            children = List.of();
        }

        final boolean captureSeat = !children.isEmpty() && AyameDSCompat.isDragon(node);
        if (captureSeat) {
            AyameDSCompat.beginSeatCapture(node, poseStack.last().pose());
        }

        SeatTransform seat = null;
        try {
            final float rotationYaw = rotationMode == RotationMode.UNLOCK ? node.getYRot() : 0.0F;
            final int packedLight = getLight(node, partialTicks);
            RenderSystem.runAsFancy(() -> entityRenderDispatcher.render(node, 0.0, 0.0, 0.0,
                    rotationYaw, partialTicks, poseStack, bufferSource, packedLight));
            if (captureSeat) {
                seat = AyameDSCompat.takeSeatTransform();
            }
        } finally {
            if (captureSeat) {
                AyameDSCompat.endSeatCapture();
            }
        }

        for (LivingEntity child : children) {
            poseStack.pushPose();
            try {
                if (seat != null) {
                    // translate(seat) * rotate(bone tilt) * translate(-pivot). DS's render
                    // correction wins over CarryOn when the mount has a MountingBone.
                    poseStack.translate(seat.position().x, seat.position().y, seat.position().z);
                    poseStack.mulPose(seat.tilt());
                    Vec3 pivot = child.getVehicleAttachmentPoint(node);
                    poseStack.translate(-pivot.x, -pivot.y, -pivot.z);

                    if (CONFIGS.debugMode.getValue()) {
                        long now = System.nanoTime();
                        if (now - lastChainDebugLogNanos > 1_000_000_000L) {
                            lastChainDebugLogNanos = now;
                            AyamePaperDoll.LOGGER.info(
                                    "[Debug] chain {} -> {}: seat=({}, {}, {}), tilt=({}, {}, {}, {}), pivot=({}, {}, {})",
                                    node.getName().getString(), child.getName().getString(),
                                    seat.position().x, seat.position().y, seat.position().z,
                                    seat.tilt().x, seat.tilt().y, seat.tilt().z, seat.tilt().w,
                                    pivot.x, pivot.y, pivot.z);
                        }
                    }
                } else if (AyameDSCompat.isCarriedPassenger(node, child)) {
                    // No MountingBone: CarryOn's front-carry offset is what the world shows.
                    Vec3 carryOffset = new Vec3(0.0, 0.4, 0.6)
                            .yRot((float) Math.toRadians(-node.yBodyRot));
                    poseStack.translate(carryOffset.x, carryOffset.y, carryOffset.z);
                    logChainVector("[Debug] chain (CarryOn) {} -> {}: offset=({}, {}, {})",
                            node, child, carryOffset);
                } else {
                    // DS fallback without a MountingBone: vanilla PASSENGER point, no tilt.
                    int passengerIndex = Math.max(0, node.getPassengers().indexOf(child));
                    Vec3 attachment = node.getDimensions(node.getPose()).attachments()
                            .getClamped(EntityAttachment.PASSENGER, passengerIndex, node.getYRot());
                    poseStack.translate(attachment.x, attachment.y, attachment.z);
                    Vec3 pivot = child.getVehicleAttachmentPoint(node);
                    poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
                    logChainVector("[Debug] chain (no MountingBone fallback) {} -> {}: attachment=({}, {}, {})",
                            node, child, attachment);
                }
                renderChainNode(poseStack, child, renderedChildren, rotationMode, partialTicks,
                        bufferSource, entityRenderDispatcher);
            } finally {
                poseStack.popPose();
            }
        }
    }

    private static void logChainVector(String format, LivingEntity carrier, LivingEntity rider, Vec3 vector) {
        if (!CONFIGS.debugMode.getValue()) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastChainDebugLogNanos <= 1_000_000_000L) {
            return;
        }
        lastChainDebugLogNanos = now;
        AyamePaperDoll.LOGGER.info(format, carrier.getName().getString(), rider.getName().getString(),
                vector.x, vector.y, vector.z);
    }

    private double getPoseOffsetY(LivingEntity targetEntity, float partialTicks, Configs.PoseOffsetMethod poseOffsetMethod) {
        // These humanoid eye-height offsets would push a scaled dragon far off frame.
        if (AyameDSCompat.isDragon(targetEntity)) {
            return 0;
        }

        if (poseOffsetMethod == Configs.PoseOffsetMethod.AUTO) {
            final float defaultPlayerEyeHeight = Player.DEFAULT_EYE_HEIGHT;
            final float defaultPlayerSwimmingBBHeight = Player.SWIMMING_BB_HEIGHT;
            final float eyeHeightRatio = 0.85f;
            if (targetEntity.isFallFlying()) {
                return (defaultPlayerEyeHeight - targetEntity.getEyeHeight()) * getFallFlyingLeaning(targetEntity, partialTicks);
            } else if (targetEntity.isAutoSpinAttack()) {
                return defaultPlayerEyeHeight - defaultPlayerSwimmingBBHeight * eyeHeightRatio * 0.8;
            } else if (targetEntity.isVisuallySwimming()) {
                return targetEntity.getSwimAmount(partialTicks) <= 0 ? 0 : defaultPlayerEyeHeight - targetEntity.getEyeHeight();
            } else if (!targetEntity.isVisuallySwimming() && targetEntity.getSwimAmount(partialTicks) > 0) { // for swimming/crawling pose, only smooth the falling edge
                return (defaultPlayerEyeHeight - defaultPlayerSwimmingBBHeight * eyeHeightRatio * 0.85) * targetEntity.getSwimAmount(partialTicks);
            } else {
                return Player.DEFAULT_EYE_HEIGHT - targetEntity.getEyeHeight();
            }
        } else if (poseOffsetMethod == Configs.PoseOffsetMethod.MANUAL) {
            if (targetEntity.isFallFlying()) {
                return CONFIGS.elytraOffsetY.getValue() * getFallFlyingLeaning(targetEntity, partialTicks);
            } else if ((targetEntity.isVisuallySwimming()) && targetEntity.getSwimAmount(partialTicks) > 0 || targetEntity.isAutoSpinAttack()) { // require nonzero leaning to filter out glitch
                return CONFIGS.swimCrawlOffsetY.getValue();
            } else if (!targetEntity.isVisuallySwimming() && targetEntity.getSwimAmount(partialTicks) > 0) { // for swimming/crawling pose, only smooth the falling edge
                return CONFIGS.swimCrawlOffsetY.getValue() * targetEntity.getSwimAmount(partialTicks);
            } else if (targetEntity.isCrouching()) {
                return CONFIGS.sneakOffsetY.getValue();
            }
        }
        return 0;
    }

    private void transformEntity(LivingEntity targetEntity, float partialTicks, boolean forceStanding) {
        // synchronize values to remove glitch
        if (!targetEntity.isSwimming() && !targetEntity.isFallFlying() && !targetEntity.isVisuallyCrawling()) {
            targetEntity.setPose(targetEntity.isCrouching() ? Pose.CROUCHING : Pose.STANDING);
        }

        if (forceStanding) {
            if (targetEntity instanceof LocalPlayer player) {
                player.crouching = false;
            }
            targetEntity.vehicle = null;

            targetEntity.swimAmount = 0;
            targetEntity.swimAmountO = 0;

            targetEntity.setSharedFlag(7, false);
            targetEntity.fallFlyTicks = 0;
        }

        // FIXME: NEVERFIX - glitch when the mouse moves too fast, caused by lerping a warped value, it is possibly wrapped in LivingEntity#tick or LivingEntity#turnHead
        final float headLerp = Mth.lerp(partialTicks, targetEntity.yHeadRotO, targetEntity.yHeadRot);
        final double headYaw = CONFIGS.headYaw.getValue(), headYawRange = CONFIGS.headYawRange.getValue();
        final double bodyYaw = CONFIGS.bodyYaw.getValue(), bodyYawRange = CONFIGS.bodyYawRange.getValue();
        final double pitch = CONFIGS.pitch.getValue(), pitchRange = CONFIGS.pitchRange.getValue();
        final float headClamp = (float) Mth.clamp(headLerp, headYaw - headYawRange, headYaw + headYawRange);
        final float bodyLerp = Mth.lerp(partialTicks, targetEntity.yBodyRotO, targetEntity.yBodyRot);
        final float diff = headLerp - bodyLerp;
        final float bodyClamp = (float) Mth.clamp(Mth.wrapDegrees(headClamp - diff), bodyYaw - bodyYawRange, bodyYaw + bodyYawRange);
        final float pitchClamp = (float) (Mth.clamp(Mth.lerp(partialTicks, targetEntity.xRotO, targetEntity.getXRot()), -pitchRange, pitchRange) + pitch);
        final RotationMode rotationMode = CONFIGS.rotationMode.getValue();

        // 头部锁定
        if (rotationMode == RotationMode.LOCK) {//|| (targetEntity.ayame_paperdoll$isSitting() && (rotationMode == RotationMode.SMOOTH_LOCK))) {
            targetEntity.yHeadRot = targetEntity.yHeadRotO = 180 - headClamp;
        }
        // 身体锁定
        if (rotationMode == RotationMode.LOCK) {//|| (targetEntity.ayame_paperdoll$isSitting() && (rotationMode == RotationMode.SMOOTH_LOCK))) {
            targetEntity.yBodyRot = targetEntity.yBodyRotO = 180 - bodyClamp;
        }

        // 头部俯视角度
        targetEntity.setXRot(targetEntity.xRotO = pitchClamp);


        if (!CONFIGS.swingHands.getValue()) {
            targetEntity.attackAnim = 0;
            targetEntity.oAttackAnim = 0;
        }

        if (!CONFIGS.hurtFlash.getValue()) {
            targetEntity.hurtTime = 0;
        }

        targetEntity.setRemainingFireTicks(0);

        targetEntity.setSharedFlag(0, false);
    }

    @SuppressWarnings("deprecation")
    private void performRendering(Entity targetEntity, double posX, double posY, double size, boolean mirror,
                                  Vector3f offset, double lightDegree, float partialTicks, GuiGraphics guiGraphics) {
        performRendering(targetEntity, posX, posY, size, mirror, offset, lightDegree, partialTicks, guiGraphics, 1.0f);
    }

    @SuppressWarnings("deprecation")
    private void performRendering(Entity targetEntity, double posX, double posY, double size, boolean mirror,
                                  Vector3f offset, double lightDegree, float partialTicks, GuiGraphics guiGraphics,
                                  float fitScale) {
        EntityRenderDispatcher entityRenderDispatcher = minecraft.getEntityRenderDispatcher();

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.scale(1, 1, -1);
        modelViewStack.rotateY((float) Math.toRadians(lightDegree + 180));
        RenderSystem.applyModelViewMatrix();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(Axis.YP.rotationDegrees(-(float) lightDegree - 180));
        poseStack.translate(posX, posY, 0);
        // X mirror INNERMOST (closest to model vertex): mirrors raw geometry
        // coordinates so the model faces the camera instead of away from it.
        if (mirror) poseStack.scale(-1, 1, 1);
        poseStack.scale((float) size, (float) size, (float) size);
        // Auto-scale an oversized dragon back to human height so it doesn't overflow the
        // paper doll. Applied to the local poseStack only; the provider returns 1.0 for
        // non-dragons, and when the feature is disabled this block is skipped entirely.
        if (CONFIGS.autoScaleDragonToHumanHeight.getValue()) {
            float dragonScaleFactor = AyameDSCompat.getDragonScaleFactor(targetEntity);
            if (dragonScaleFactor != 1.0f) {
                poseStack.scale(dragonScaleFactor, dragonScaleFactor, dragonScaleFactor);
            }
        }
        if (fitScale != 1.0f) {
            poseStack.scale(fitScale, fitScale, fitScale);
        }
        Quaternionf zRot = new Quaternionf().rotateZ((float) Math.PI);

        final RotationMode rotationMode = CONFIGS.rotationMode.getValue();

        Quaternionf xyzRot;
        /*if (rotationMode == RotationMode.SMOOTH_LOCK && !(targetEntity instanceof Boat)) {
            xyzRot = new Quaternionf().rotateXYZ(
                    (float) Math.toRadians(CONFIGS.rotationX.getValue()),
                    (float) ((targetEntity.getYRot() + CONFIGS.rotationY.getValue() - 180) * ((float) Math.PI / 180F)),
                    (float) Math.toRadians(CONFIGS.rotationZ.getValue()));
        } else {
            8}*/
        xyzRot = new Quaternionf().rotateXYZ(
                (float) Math.toRadians(CONFIGS.rotationX.getValue()),
                (float) Math.toRadians(CONFIGS.rotationY.getValue()),
                (float) Math.toRadians(CONFIGS.rotationZ.getValue()));


        zRot.mul(xyzRot);
        poseStack.mulPose(zRot);

        if (targetEntity instanceof Boat) {
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180)));
        }

        Lighting.setupForEntityInInventory();
        xyzRot.conjugate();

        entityRenderDispatcher.overrideCameraOrientation(xyzRot);
        boolean renderHitbox = entityRenderDispatcher.shouldRenderHitBoxes();
        entityRenderDispatcher.setRenderHitBoxes(false);
        entityRenderDispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();


        final float rotationYaw = ((rotationMode == RotationMode.UNLOCK) /*|| (rotationMode == RotationMode.SMOOTH_LOCK)*/) ? targetEntity.getYRot() : 0.0F;

        if (CONFIGS.debugMode.getValue()) {
            AyamePaperDoll.LOGGER.info(
                    "[Debug] performRendering: mirror={} size={} entity={} forceDisableCulling={}",
                    mirror, size, targetEntity.getClass().getSimpleName(),
                    CONFIGS.debugForceDisableCulling.getValue());
        }

        // Mirror X-flip reverses triangle winding. Combined with the GPU
        // Z-inversion, front faces end up CW in window space, which default
        // culling (GL_BACK) would discard. Flip to GL_FRONT to keep them.
        // Applied as global GL state so it survives mods that bypass or
        // replace BufferSource.endBatch (e.g. ImmediatelyFast).
        // TODO: 修复矿车锁定旋转时不被锁定的问题
        final boolean needCullFix = needsCullFix(mirror);

        if (needCullFix) {
            applyCullFix(mirror);
        }

        bufferSource.ayame_PaperDoll$setFlipCullFace(mirror);
        AyameDSCompat.pushCompatRendering();
        RenderSystem.runAsFancy(
                () -> entityRenderDispatcher.render(targetEntity, offset.x, offset.y, offset.z, rotationYaw, partialTicks, poseStack, bufferSource, getLight(targetEntity, partialTicks))
        );
        AyameDSCompat.popCompatRendering();

        bufferSource.endBatch();
        bufferSource.ayame_PaperDoll$setFlipCullFace(false);

        if (needCullFix) {
            restoreCullFix();
        }

        // do not need to restore this value in fact
        entityRenderDispatcher.setRenderShadow(true);
        entityRenderDispatcher.setRenderHitBoxes(renderHitbox);

        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
    }

    private static boolean needsCullFix(boolean mirror) {
        return mirror || (CONFIGS.debugMode.getValue() && CONFIGS.debugForceDisableCulling.getValue());
    }

    private static void applyCullFix(boolean mirror) {
        if (CONFIGS.debugMode.getValue() && CONFIGS.debugForceDisableCulling.getValue()) {
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (CONFIGS.debugLogCullFace.getValue()) {
                AyamePaperDoll.LOGGER.info("[Debug] GL direct: glDisable(GL_CULL_FACE) pre-render");
            }
        } else if (mirror) {
            GL11.glCullFace(GL11.GL_FRONT);
            if (CONFIGS.debugMode.getValue() && CONFIGS.debugLogCullFace.getValue()) {
                int cullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
                AyamePaperDoll.LOGGER.info("[Debug] GL direct: glCullFace(GL_FRONT) pre-render, result=0x{}",
                        String.format("%X", cullMode));
            }
        }
    }

    private static void restoreCullFix() {
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        if (CONFIGS.debugMode.getValue() && CONFIGS.debugLogCullFace.getValue()) {
            AyamePaperDoll.LOGGER.info("[Debug] GL direct: restored glEnable+glCullFace(GL_BACK) post-render");
        }
    }
}