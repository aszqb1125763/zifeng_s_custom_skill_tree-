package org.zifeng.skilltree.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.init.ModEntities;

import java.util.Optional;
import java.util.UUID;

/**
 * 杀戮光环·飞剑实体（2026-10-21，替换原来的「客户端纯视觉 + 服务端延迟结算」方案）。
 *
 * <h2>为什么改用实体</h2>
 * 时序延迟不可靠（攻击间隔、TPS、网络都会让「伤害」和「飞剑命中」错位）。
 * 改成真实体后，<b>伤害由碰撞事件触发</b>，天然同步：剑碰到生物的那一刻才结算。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li>服务端逐 tick 朝目标（生成时锁定的生物 UUID）飞行，速度 {@link #SPEED} 格/tick；</li>
 *   <li>与任何<b>生物</b>的碰撞箱相交 → 以碰撞点为中心调用
 *       {@link AuraEvents#blastSphere}（半径 10 格球形秒杀），然后自身消失；</li>
 *   <li>靠近目标 1.2 格内（目标太小/移动快导致没判定到碰撞）也视为命中；</li>
 *   <li>存活超过 {@link #MAX_LIFE} tick（目标死亡/丢失）自动消失，避免残留。</li>
 * </ul>
 *
 * <p>纯服务端逻辑 + 原版实体同步；客户端只负责画一把剑（{@code AuraSwordEntityRenderer}）。
 */
public class AuraSwordEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(AuraSwordEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> TARGET =
            SynchedEntityData.defineId(AuraSwordEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 基础飞行速度（格/tick）：0.5 —— 配合加速曲线，10 格约 0.6 秒，能看清“越飞越快” */
    private static final double SPEED = 0.5;
    /** 最长存活 tick（防止目标丢失后一直飞） */
    private static final int MAX_LIFE = 120;
    /** 命中判定：与生物碰撞箱的额外膨胀（格） */
    private static final double HIT_INFLATE = 0.45;
    /** 贴脸判定距离平方（1.2 格） */
    private static final double HIT_DISTANCE_SQR = 1.44;

    /** 目标模式（敌对/友好/所有），不参与同步，服务端自己用 */
    private int mode;
    private int life;
    /** 光环伤害结算参数（生成时快照，命中时按同样等级结算） */
    private float auraDamage;
    private boolean voidSpear;
    private boolean empower;
    private boolean ignoreIFrames;

    public AuraSwordEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
        this.setNoGravity(true);
    }

    /** 飞行速度倍率（速度光环等级越高越大） */
    private float speedMultiplier = 1.0f;

    public AuraSwordEntity(Level level, UUID ownerUuid, UUID targetUuid, int mode,
                           float auraDamage, boolean voidSpear, boolean empower, boolean ignoreIFrames,
                           float speedMultiplier) {
        this(ModEntities.AURA_SWORD.get(), level);
        this.entityData.set(OWNER, Optional.ofNullable(ownerUuid));
        this.entityData.set(TARGET, Optional.ofNullable(targetUuid));
        this.mode = mode;
        this.auraDamage = auraDamage;
        this.voidSpear = voidSpear;
        this.empower = empower;
        this.ignoreIFrames = ignoreIFrames;
        this.speedMultiplier = Math.max(0.2f, speedMultiplier);
    }

    /** 是否按光环等级结算（false 时退回原来的无条件硬杀，兼容旧存档里的飞剑实体） */
    public boolean hasAuraStats() {
        return auraDamage > 0.0f;
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER).orElse(null);
    }

    public UUID getTargetUuid() {
        return entityData.get(TARGET).orElse(null);
    }

    public int getMode() {
        return mode;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(TARGET, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (++life > MAX_LIFE) {
            discard();
            return;
        }
        final ServerLevel serverLevel = (ServerLevel) level();
        final UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null) {
            discard();
            return;
        }
        final net.minecraft.world.entity.player.Player ownerRaw = serverLevel.getPlayerByUUID(ownerUuid);
        if (!(ownerRaw instanceof ServerPlayer owner) || !owner.isAlive()) {
            discard();
            return;
        }

        // —— 朝目标飞行（目标每 tick 重新取，能跟着生物移动）——
        final UUID targetUuid = getTargetUuid();
        LivingEntity target = null;
        if (targetUuid != null && serverLevel.getEntity(targetUuid) instanceof LivingEntity living && living.isAlive()) {
            target = living;
            final Vec3 dest = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            final Vec3 delta = dest.subtract(position());
            if (delta.lengthSqr() > 1.0E-4) {
                final Vec3 dir = delta.normalize();
                // ★ 灵动化：在直线方向上叠加一段【侧向正弦摆动】，让路径呈波浪弧线而不是死直
                Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
                if (side.lengthSqr() < 1.0E-6) {
                    side = new Vec3(1.0, 0.0, 0.0);
                }
                side = side.normalize();
                final double wave = Math.sin(life * 0.45) * 0.12;
                // ★ 加速：起飞慢、越飞越快（与客户端 pow(p,1.5) 的观感对齐）
                // ★ 2026-10-22 强加速：起步 0.2 格/tick → 末段约 1.5 格/tick（3 倍）
                final double accel = Math.min(3.0, 0.4 + life * 0.12);
                Vec3 step = dir.scale(SPEED * speedMultiplier * accel).add(side.scale(wave));
                if (step.length() > delta.length()) {
                    step = delta;
                }
                setPos(position().add(step));
                // 朝向 = 实际速度方向（含摆动），剑尖始终顺着飞行轨迹
                setYRot((float) (Math.atan2(step.z, step.x) * 180.0 / Math.PI));
                setXRot(0.0f);
            }
        }

        // —— 碰撞检测：与任意生物碰撞箱相交 = 命中 ——
        final var hits = serverLevel.getEntities(this, getBoundingBox().inflate(HIT_INFLATE),
                e -> e instanceof LivingEntity && !e.getUUID().equals(ownerUuid));
        if (!hits.isEmpty()) {
            explode(serverLevel, owner);
            return;
        }
        // 贴脸兜底（目标移动快 / 碰撞箱小）
        if (target != null && distanceToSqr(target) <= HIT_DISTANCE_SQR) {
            explode(serverLevel, owner);
        }
    }

    /** 以当前飞剑位置为中心，触发光圈爆炸（球形秒杀 + 客户端红环） */
    private void explode(ServerLevel level, ServerPlayer owner) {
        final Vec3 center = position();
        // 客户端红环（纯视觉）
        org.zifeng.skilltree.network.AuraBurstS2CPacket.sendNear(level, center);
        // 服务端球形秒杀（走虚空之矛硬杀链路）
        if (hasAuraStats()) {
            // 按生成时快照的【光环等级/强化】结算（用户要求：飞剑伤害 = 对应等级的光环伤害）
            AuraEvents.blastSphere(owner, level, center, mode, auraDamage, voidSpear, empower, ignoreIFrames);
        } else {
            AuraEvents.blastSphere(owner, level, center, mode);
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
        if (tag.hasUUID("Target")) {
            entityData.set(TARGET, Optional.of(tag.getUUID("Target")));
        }
        mode = tag.getInt("Mode");
        life = tag.getInt("Life");
        speedMultiplier = Math.max(0.2f, tag.getFloat("SpeedMul"));
        auraDamage = tag.getFloat("AuraDamage");
        voidSpear = tag.getBoolean("VoidSpear");
        empower = tag.getBoolean("Empower");
        ignoreIFrames = tag.getBoolean("IgnoreIFrames");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        final UUID owner = getOwnerUuid();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        final UUID target = getTargetUuid();
        if (target != null) {
            tag.putUUID("Target", target);
        }
        tag.putInt("Mode", mode);
        tag.putInt("Life", life);
        tag.putFloat("SpeedMul", speedMultiplier);
        tag.putFloat("AuraDamage", auraDamage);
        tag.putBoolean("VoidSpear", voidSpear);
        tag.putBoolean("Empower", empower);
        tag.putBoolean("IgnoreIFrames", ignoreIFrames);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0;
    }
}
