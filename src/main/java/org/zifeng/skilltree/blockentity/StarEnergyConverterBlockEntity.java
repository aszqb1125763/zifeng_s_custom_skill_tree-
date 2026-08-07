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
import org.jetbrains.annotations.NotNull;
import org.zifeng.skilltree.Config;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.menu.StarEnergyConverterMenu;
import org.zifeng.skilltree.init.ModBlockEntities;

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

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getProgressPercent();
                case 1 -> totalConverted.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
                case 2 -> ownerUUID != null ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 3;
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

    private BigInteger getThreshold() {
        return BigInteger.valueOf(Math.max(1, Config.ENERGY_PER_SKILL_POINT.get()));
    }

    /** 由方块 getTicker 驱动的服务端每 tick 逻辑 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, StarEnergyConverterBlockEntity be) {
        if (level == null || level.isClientSide) {
            return;
        }
        long gameTime = level.getGameTime();
        if (be.lastReceiveTick < 0 || gameTime - be.lastReceiveTick > 1) {
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
            // 全能精通：技能点获取速度 -20%
            double rate = org.zifeng.skilltree.skill.SkillEffects.getSkillPointRate(record);
            double granted = amount * rate;
            if (granted > 0) {
                record.addSkillPoints(granted);
            }
            data.setDirty();
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
