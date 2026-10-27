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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import com.hoz.ayamedscompat.AyameDSCompat;
import org.ayamemc.ayamepaperdoll.config.Configs;
import org.ayamemc.ayamepaperdoll.config.Configs.RotationMode;
import org.ayamemc.ayamepaperdoll.hud.DataBackup.DataBackupEntry;
import org.ayamemc.ayamepaperdoll.hud.ModRenderState.ChainChild;
import org.ayamemc.ayamepaperdoll.hud.ModRenderState.ChainNode;
import org.ayamemc.ayamepaperdoll.mixininterface.GuiGraphicsExtractorInterface;
import org.jetbrains.annotations.Nullable;
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
            return LightCoordsUtil.pack(blockLight, skyLight);
        }
        return LightCoordsUtil.pack(15, 15);
    }

    private static float getFallFlyingLeaning(LivingEntity entity, float partialTicks) {
        float ticks = partialTicks + entity.getFallFlyingTicks();
        return Mth.clamp(ticks * ticks / 100f, 0f, 1f);
    }

    public static boolean shouldLockRotationYaw() {
        final RotationMode rotationUnlock = CONFIGS.rotationMode.getValue();
        return (rotationUnlock == RotationMode.LOCK); //|| (rotationUnlock == RotationMode.SMOOTH_LOCK)*/;

    }

    // 这会导致织布机渲染问题，不要使用↓
    // follow convention in LayeredDrawer#renderInternal
    // guiGraphics.pose().translate(0, 0, 200);

    /**
     * Mimics the code in {@link InventoryScreen#extractEntityInInventoryFollowsMouse}
     */
    public void extractPaperdoll(GuiGraphicsExtractor graphics, float a) {
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

        Configs.PoseOffsetMethod poseOffsetMethod = CONFIGS.poseOffsetMethod.getValue();

        List<LivingEntity> ridingChain = buildRidingChain(targetEntity, poseOffsetMethod);
        if (ridingChain != null) {
            extractRidingChain(graphics, ridingChain, targetEntity, poseOffsetMethod, a);
            return;
        }

        var backup = new DataBackup<>(targetEntity, LIVINGENTITY_BACKUP_ENTRIES);
        backup.save();

        transformEntity(targetEntity, a, poseOffsetMethod == Configs.PoseOffsetMethod.FORCE_STANDING);

        EntityRenderState vehicleRenderState = null;
        Vector3f vehicleOffset = null;
        DataBackup<LivingEntity> vehicleBackup = null;
        if (CONFIGS.renderVehicle.getValue() && poseOffsetMethod != Configs.PoseOffsetMethod.FORCE_STANDING && targetEntity.isPassenger()) {
            var vehicle = targetEntity.getVehicle();
            assert vehicle != null;

            // get the overall yaw before transforming
            var yawLerped = vehicle.getViewYRot(a);

            // FIXME: NEVERFIX - the rendered yaw of minecart is determined non-trivially in its MinecartEntityRenderer#render, so it cannot be fixed to 0 easily
            if (vehicle instanceof LivingEntity livingVehicle) {
                vehicleBackup = new DataBackup<>(livingVehicle, LIVINGENTITY_BACKUP_ENTRIES);
                vehicleBackup.save();
                transformEntity(livingVehicle, a, false);
            }

            vehicleRenderState = extractRenderState(vehicle, a);
            vehicleOffset = vehicle.getPosition(a).subtract(targetEntity.getPosition(a))
                    .yRot((float) Math.toRadians(yawLerped+180)).toVector3f();// undo the rotation
        }

        var targetRenderState = extractRenderState(targetEntity, a);

        extractPaperdoll(
                graphics,
                targetRenderState,
                new Vector3f(0, (float) getPoseOffsetY(targetEntity, a, poseOffsetMethod), 0),
                vehicleRenderState,
                vehicleOffset
        );

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

    // Seat transforms are captured at render time (bones do not exist yet), so this only stores
    // the pivots/fallbacks and the node hierarchy in one PiP state.
    private void extractRidingChain(GuiGraphicsExtractor graphics, List<LivingEntity> chain,
                                    LivingEntity targetEntity, Configs.PoseOffsetMethod poseOffsetMethod,
                                    float partialTicks) {
        final LivingEntity root = chain.get(0);
        List<DataBackup<LivingEntity>> backups = new ArrayList<>(chain.size());
        for (LivingEntity entity : chain) {
            backups.add(new DataBackup<>(entity, LIVINGENTITY_BACKUP_ENTRIES));
        }

        try {
            for (DataBackup<LivingEntity> backup : backups) {
                backup.save();
            }
            for (LivingEntity entity : chain) {
                transformEntity(entity, partialTicks, false);
            }

            Map<LivingEntity, List<LivingEntity>> renderedChildren = buildRenderedChildren(chain);

            Map<LivingEntity, EntityRenderState> states = new IdentityHashMap<>();
            for (LivingEntity entity : chain) {
                // groupScale handles auto-scale for the whole chain. Chain children drop their
                // own glide tilt because the MountingBone seat already carries the mount's tilt.
                states.put(entity, extractRenderState(entity, partialTicks, false, entity != root));
            }

            float groupScale = 1.0f;
            if (CONFIGS.autoScaleDragonToHumanHeight.getValue()
                    && AyameDSCompat.isDragon(root)
                    && states.get(root) instanceof LivingEntityRenderState livingRoot
                    && livingRoot.scale > 0.001f) {
                groupScale = 1.0f / livingRoot.scale;
            }
            float fitScale = getChainFitScale(chain, root, groupScale);
            if (fitScale != 1.0f && CONFIGS.debugMode.getValue()) {
                long now = System.nanoTime();
                if (now - lastChainFitLogNanos > 1_000_000_000L) {
                    lastChainFitLogNanos = now;
                    org.ayamemc.ayamepaperdoll.AyamePaperDoll.LOGGER.info(
                            "[Debug] auto-fit riding chain: nodes={}, fit={}", chain.size(), fitScale);
                }
            }

            Map<LivingEntity, ChainNode> nodes = new IdentityHashMap<>();
            ChainNode rootNode = buildChainNode(root, root, targetEntity, states, renderedChildren, nodes,
                    poseOffsetMethod, partialTicks);

            extractPaperdoll(graphics,
                    rootNode.renderState(),
                    new Vector3f(0, 0, 0),
                    null,
                    null,
                    rootNode,
                    groupScale,
                    fitScale);
        } finally {
            for (int i = backups.size() - 1; i >= 0; i--) {
                backups.get(i).restore();
            }
        }
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

    private ChainNode buildChainNode(LivingEntity entity, LivingEntity root, LivingEntity targetEntity,
                                     Map<LivingEntity, EntityRenderState> states,
                                     Map<LivingEntity, List<LivingEntity>> renderedChildren,
                                     Map<LivingEntity, ChainNode> nodes,
                                     Configs.PoseOffsetMethod poseOffsetMethod, float partialTicks) {
        ChainNode cached = nodes.get(entity);
        if (cached != null) {
            return cached;
        }

        List<LivingEntity> children = renderedChildren.getOrDefault(entity, List.of());
        List<ChainChild> childNodes = new ArrayList<>(children.size());
        for (LivingEntity child : children) {
            int index = Math.max(0, entity.getPassengers().indexOf(child));
            Vec3 pivot = child.getVehicleAttachmentPoint(entity);
            Vec3 fallback = entity.getDimensions(entity.getPose()).attachments()
                    .getClamped(EntityAttachment.PASSENGER, index, entity.getYRot());

            Vec3 carryOnOffset = null;
            if (!AyameDSCompat.isDragon(entity) && AyameDSCompat.isCarriedPassenger(entity, child)) {
                carryOnOffset = new Vec3(0.0, 0.4, 0.6)
                        .yRot((float) Math.toRadians(-entity.yBodyRot));
            }

            ChainNode childNode = buildChainNode(child, root, targetEntity, states, renderedChildren, nodes,
                    poseOffsetMethod, partialTicks);
            childNodes.add(new ChainChild(
                    childNode,
                    index,
                    pivot.toVector3f(),
                    fallback.toVector3f(),
                    carryOnOffset == null ? null : carryOnOffset.toVector3f()
            ));
        }

        // Pose-offset only applies to the chain origin; a riding target is placed by the seat.
        Vector3f translation = new Vector3f();
        if (entity == targetEntity && entity == root) {
            translation.y = (float) getPoseOffsetY(entity, partialTicks, poseOffsetMethod);
        }

        ChainNode node = new ChainNode(
                states.get(entity),
                AyameDSCompat.isDragon(entity),
                translation,
                List.copyOf(childNodes),
                entity.getName().getString()
        );
        nodes.put(entity, node);
        return node;
    }

    /** Conservative screen-fit scale for a multi-node chain; 1.0 when it fits. */
    private static float getChainFitScale(List<LivingEntity> chain, LivingEntity root, float groupScale) {
        if (!CONFIGS.autoFitRidingChain.getValue() || chain.size() <= 1) {
            return 1.0f;
        }

        float rootHeight = Math.max(1.0f, root.getDimensions(root.getPose()).height() * groupScale);
        float chainHeight = rootHeight + (chain.size() - 1) * Math.max(1.0f, rootHeight * 0.7f);
        double size = CONFIGS.size.getValue();
        double anchor = CONFIGS.offsetY.getValue();
        // The doll grows upward from the anchor. Presets with the anchor below the screen can use
        // almost the whole screen height; top-corner presets only have the space above the anchor.
        float available = anchor >= 1.0 ? 0.95f : (float) Math.max(0.06, Math.min(0.95, anchor - 0.05));
        float needed = (float) (chainHeight * size);
        if (needed <= available) {
            return 1.0f;
        }
        return Math.max(0.15f, available / needed);
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
        if (rotationMode == RotationMode.LOCK) {
            targetEntity.yHeadRot = targetEntity.yHeadRotO = 180 - headClamp;
        }
        // 身体锁定
        if (rotationMode == RotationMode.LOCK) {
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

    private void extractPaperdoll(GuiGraphicsExtractor graphics, EntityRenderState target, Vector3f offset,
                                  @Nullable EntityRenderState vehicle, @Nullable Vector3f offset2) {
        extractPaperdoll(graphics, target, offset, vehicle, offset2, null, 1.0f, 1.0f);
    }

    private void extractPaperdoll(GuiGraphicsExtractor graphics, EntityRenderState target, Vector3f offset,
                                  @Nullable EntityRenderState vehicle, @Nullable Vector3f offset2,
                                  @Nullable ChainNode chain, float groupScale, float fitScale) {
        var scaledWidth = minecraft.getWindow().getGuiScaledWidth();
        var scaledHeight = minecraft.getWindow().getGuiScaledHeight();

        var posX = CONFIGS.offsetX.getValue() * scaledWidth;
        var posY = CONFIGS.offsetY.getValue() * scaledHeight;
        var size = CONFIGS.size.getValue() * scaledHeight;
        var lightDegree = CONFIGS.lightDegree.getValue();

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.PI);
        Quaternionf configRot = new Quaternionf().rotateXYZ(
                (float) Math.toRadians(CONFIGS.rotationX.getValue()),
                (float) Math.toRadians(CONFIGS.rotationY.getValue()),
                (float) Math.toRadians(CONFIGS.rotationZ.getValue()));

        pose.mul(configRot).rotateY((float) Math.toRadians(lightDegree + 180));

        var pose1 = new Quaternionf(pose);
        if(target instanceof BoatRenderState) {
            pose1.rotateY((float) Math.toRadians(180));
        }
        if(vehicle instanceof BoatRenderState boat){
            pose.rotateY((float) Math.toRadians(180));
            if(PaperDollRenderer.shouldLockRotationYaw()){
                boat.yRot = 0;
            }
        }

        // different renderState, offset, pose; same rotation, pos, size
        if (chain != null) {
            ((GuiGraphicsExtractorInterface) graphics).addPicturesInPictureState(
                    new ModRenderState(
                            target,
                            offset,
                            pose1,
                            chain,
                            groupScale,
                            fitScale,
                            new Quaternionf(configRot).conjugate(),
                            (int) posX, (int) posY, (float) size, null
                    )
            );
            return;
        }
        ((GuiGraphicsExtractorInterface)graphics).addPicturesInPictureState(
                new ModRenderState(
                        target,
                        offset,
                        pose1,
                        vehicle,
                        offset2,
                        pose,
                        new Quaternionf(configRot).conjugate(),
                        (int) posX, (int) posY,(float) size, null
                )
        );
    }

    private EntityRenderState extractRenderState(Entity targetEntity, float a) {
        return extractRenderState(targetEntity, a, CONFIGS.autoScaleDragonToHumanHeight.getValue(), false);
    }

    //also check =InventoryScreen#extractRenderState
    private EntityRenderState extractRenderState(Entity targetEntity, float a, boolean autoScaleDragon, boolean chainChild) {
        // DS compat: for a dragon player, use DragonSurvival's UI render state so the paper
        // doll shows the dragon instead of the human model. The pose is the paper doll's locked
        // values (yBodyRot / head aligned / xRot); auto-scale is the config-driven toggle.
        // Falls back to the vanilla state when the entity is not a dragon or DS is absent.
        if (targetEntity instanceof LivingEntity livingEntity) {
            EntityRenderState dragonState = AyameDSCompat.createDragonUIRenderState(
                    livingEntity, a,
                    livingEntity.yBodyRot, 0.0, livingEntity.getXRot(),
                    autoScaleDragon, chainChild);
            if (dragonState != null) {
                // DS compat active only for dragon players; gate it so normal human models
                // are never touched by the DS rendering hooks.
                AyameDSCompat.setCompatTargetDragon(true);
                dragonState.lightCoords = getLight(targetEntity, a);
                dragonState.shadowPieces.clear();
                dragonState.outlineColor = 0;
                return dragonState;
            }
        }
        AyameDSCompat.setCompatTargetDragon(false);
        EntityRenderDispatcher entityRenderDispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderer<? super Entity, ?> entityRenderer = entityRenderDispatcher.getRenderer(targetEntity);
        EntityRenderState state = entityRenderer.createRenderState(targetEntity, a);
        state.lightCoords = getLight(targetEntity, a);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        return state;
    }
}