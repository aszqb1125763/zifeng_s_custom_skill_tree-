package org.zifeng.skilltree.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.menu.StarEnergyConverterMenu;
import org.zifeng.skilltree.init.ModBlockEntities;
import org.zifeng.skilltree.network.SkillTreeDataS2CPacket;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 星能转换机方块实体：
 * <ul>
 *   <li>能量槽无限，可输入任意量 FE（receiveEnergy 全收）</li>
 *   <li>默认绑定放置者 UUID（BlockEvent.EntityPlaceEvent 监听）</li>
 *   <li>每消耗 Config.ENERGY_PER_SKILL_POINT（1 亿）能量 → 给绑定玩家 +1 技能点</li>
 *   <li>进度条不可中断：一旦停止接收能量输入（超过 1 tick），进度清空重新计算</li>
 * </ul>
 */
public class StarEnergyConverterBlockEntity extends BlockEntity implements MenuProvider {

    private BigInteger progress = BigInteger.ZERO;
    private BigInteger totalConverted = BigInteger.ZERO;
    private long lastReceiveTick = -1;
    private UUID ownerUUID;

    /** 能量输入中断判定阈值：超过 1 秒（20 tick）未收到能量输入才清空进度 */
    private static final long PROGRESS_IDLE_TICKS = 20;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getProgressPercent();
                case 1 -> getPlayerConvertedPoints().min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue(); // 玩家整体累计转换（跨机器共享）
                case 2 -> ownerUUID != null ? 1 : 0;
                case 3 -> getThreshold().min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue(); // 当前每点消耗
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            if (!simulate) {
                Level lvl = StarEnergyConverterBlockEntity.this.level;
                if (lvl != null) {
                    StarEnergyConverterBlockEntity.this.lastReceiveTick = lvl.getGameTime();
                }
                StarEnergyConverterBlockEntity.this.progress = StarEnergyConverterBlockEntity.this.progress.add(BigInteger.valueOf(maxReceive));
                StarEnergyConverterBlockEntity.this.setChanged();
            }
            return maxReceive; // 能量槽无限，全收
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0; // 只进不出
        }

        @Override
        public int getEnergyStored() {
            return progress.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE; // 槽无限
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public StarEnergyConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAR_ENERGY_CONVERTER.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ContainerData getContainerData() {
        return data;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        setChanged();
    }

    public BigInteger getProgress() {
        return progress;
    }

    public BigInteger getTotalConverted() {
        return totalConverted;
    }

    /** 0-100 进度百分比（显示用） */
    public int getProgressPercent() {
        BigInteger threshold = getThreshold();
        if (threshold.signum() <= 0) {
            return 0;
        }
        return progress.multiply(BigInteger.valueOf(100)).divide(threshold).min(BigInteger.valueOf(100)).intValue();
    }

    /**
     * 阶梯阈值：前 ENERGY_STEP_POINTS 点内，每点消耗从 ENERGY_START_COST 线性递增到 ENERGY_PER_SKILL_POINT；
     * 之后固定为最终消耗。
     * <p>⚠️ 2026-08-07 改为【玩家整体计算】：已转换点数取玩家全部机器累计（跨机器共享），
     * 不再是单台机器的 totalConverted——多台机器共享同一个阶梯进度，符合挂机多机玩法预期。
     */
    private BigInteger getThreshold() {
        long finalCost = Math.max(1, Config.ENERGY_PER_SKILL_POINT.get());
        long startCost = Math.max(1, Math.min(finalCost, Config.ENERGY_START_COST.get()));
        int step = Math.max(1, Config.ENERGY_STEP_POINTS.get());
        long converted = getPlayerConvertedPoints().min(BigInteger.valueOf(step)).longValue();
        long increment = (finalCost - startCost) / step; // 每点增量（线性递增）
        return BigInteger.valueOf(startCost + converted * increment);
    }

    /**
     * 玩家整体累计转换的技能点数（阶梯消耗依据）。
     * 未绑定 owner 或服务端不可用时降级用本机累计（保持显示/计算不崩）。
     */
    private BigInteger getPlayerConvertedPoints() {
        if (ownerUUID == null || !(level instanceof ServerLevel serverLevel)) {
            return totalConverted;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(serverLevel);
        PlayerSkillRecord record = data.getOrCreatePlayer(ownerUUID);
        return BigInteger.valueOf(record.getTotalConvertedPoints());
    }

    /** 由方块 getTicker 驱动的服务端每 tick 逻辑 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, StarEnergyConverterBlockEntity be) {
        if (level == null || level.isClientSide) {
            return;
        }
        long gameTime = level.getGameTime();
        // 能量输入中断判定：超过 1 秒（20 tick）未收到能量输入才清空进度。
        // 原来 >1 tick 就清空，导致推送间隔 >2 tick 的能量源（线缆/发电机等）进度被反复清零，永远到不了 100%。
        if (be.lastReceiveTick < 0 || gameTime - be.lastReceiveTick > PROGRESS_IDLE_TICKS) {
            // 能量输入中断（或从未输入）→ 进度直接清空，重新计算
            if (be.progress.signum() > 0) {
                be.progress = BigInteger.ZERO;
                be.setChanged();
            }
            return;
        }
        BigInteger threshold = be.getThreshold();
        if (be.progress.compareTo(threshold) >= 0) {
            BigInteger points = be.progress.divide(threshold);
            be.progress = be.progress.mod(threshold);
            be.totalConverted = be.totalConverted.add(points);
            be.grantSkillPoints(points.intValue());
            be.setChanged();
        }
    }

    /** 给绑定玩家发放技能点（玩家离线也照常累计，符合挂机玩法） */
    private void grantSkillPoints(int amount) {
        if (ownerUUID == null || amount <= 0) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            PlayerSkillSavedData data = PlayerSkillSavedData.get(serverLevel);
            PlayerSkillRecord record = data.getOrCreatePlayer(ownerUUID);
            // 玩家整体累计转换量 +amount（阶梯消耗按玩家计算，跨机器共享）
            record.addTotalConvertedPoints(amount);
            // 全能精通：技能点获取速度 -20%
            double rate = org.zifeng.skilltree.skill.SkillEffects.getSkillPointRate(record);
            double granted = amount * rate;
            if (granted > 0) {
                record.addSkillPoints(granted);
            }
            data.setDirty();
            // 实时同步：玩家在线则立即回发技能数据包，技能树界面打开时技能点实时刷新
            // （否则界面只能等 2 秒轮询，转换中的技能点显示滞后）
            net.minecraft.server.level.ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                PacketDistributor.sendToPlayer(owner, SkillTreeDataS2CPacket.from(record));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Progress", progress.toString());
        tag.putString("TotalConverted", totalConverted.toString());
        tag.putLong("LastReceiveTick", lastReceiveTick);
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            progress = new BigInteger(tag.getString("Progress"));
        } catch (NumberFormatException e) {
            progress = BigInteger.ZERO;
        }
        try {
            totalConverted = new BigInteger(tag.getString("TotalConverted"));
        } catch (NumberFormatException e) {
            totalConverted = BigInteger.ZERO;
        }
        lastReceiveTick = tag.getLong("LastReceiveTick");
        ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zifeng_s_custom_skill_tree.star_energy_converter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StarEnergyConverterMenu(containerId, inventory, this);
    }
}
