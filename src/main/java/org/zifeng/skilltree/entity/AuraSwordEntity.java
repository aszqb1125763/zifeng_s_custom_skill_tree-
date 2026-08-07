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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.zifeng.skilltree.init.ModEntities;

import java.util.Optional;
import java.util.UUID;

/**
 * 杀戮光环·钻石剑实体：绕玩家旋转环绕，无碰撞不可交互。
 * 服务端 tick 更新位置（环绕玩家），客户端渲染钻石剑模型。
 */
public class AuraSwordEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AuraSwordEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> INDEX = SynchedEntityData.defineId(AuraSwordEntity.class, EntityDataSerializers.INT);
    private static final double RADIUS = 1.2;   // 贴近身体（腿部环绕）
    private static final double HEIGHT = 0.4;   // 腿部高度
    private static final double SPIN_SPEED = 0.15;

    public AuraSwordEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public AuraSwordEntity(Level level, UUID ownerUuid, int index) {
        this(ModEntities.AURA_SWORD.get(), level);
        this.entityData.set(OWNER, Optional.ofNullable(ownerUuid));
        this.entityData.set(INDEX, index);
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER).orElse(null);
    }

    public int getIndex() {
        return entityData.get(INDEX);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(INDEX, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null) {
            discard();
            return;
        }
        Player owner = ((net.minecraft.server.level.ServerLevel) level()).getPlayerByUUID(ownerUuid);
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        // 自检查：owner 光环关闭 或 剑数已减少（<= index）→ 自己消失，保证任何关闭路径都隐藏剑
        if (owner instanceof ServerPlayer sp) {
            var record = org.zifeng.skilltree.data.PlayerSkillSavedData.get(sp.serverLevel()).getOrCreatePlayer(sp.getUUID());
            if (!record.isAuraEnabled()
                    || !record.isEnabled(org.zifeng.skilltree.skill.Skills.AURA_WEAPON)
                    || org.zifeng.skilltree.skill.SkillEffects.getAuraSwordCount(record) <= getIndex()) {
                discard();
                return;
            }
        }
        // 环绕定位：角度 = index 均分 + 随时间旋转
        long time = level().getGameTime();
        double angle = getIndex() * (Math.PI * 2 / 8.0) + time * SPIN_SPEED;
        double x = owner.getX() + Math.cos(angle) * RADIUS;
        double z = owner.getZ() + Math.sin(angle) * RADIUS;
        double y = owner.getY() + HEIGHT + Math.sin(time * 0.05 + getIndex()) * 0.08;
        setPos(x, y, z);
        setYRot((float) Math.toDegrees(angle) - 90);
        setYHeadRot(getYRot());
        // 实体免疫常驻
        setInvulnerable(true);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
        entityData.set(INDEX, tag.getInt("Index"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Index", getIndex());
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
        return distance < 16384; // 128 格
    }
}
