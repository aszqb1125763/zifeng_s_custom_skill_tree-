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
import org.zifeng.skilltree.block.SkillPointConverterBlock;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;
import org.zifeng.skilltree.init.ModBlockEntities;
import org.zifeng.skilltree.network.SkillTreeDataS2CPacket;

import java.util.UUID;

/**
 * 技能点转换机方块实体：
 * <ul>
 *   <li>能量缓冲（long 64 位，上限 Long.MAX_VALUE）受 {@link Config#MACHINE_ENERGY_CAPACITY} 上限约束</li>
 *   <li>输入速率限制：每 tick 最多接收 {@link Config#MACHINE_MAX_INPUT_RATE} FE（默认 10 万）；GUI 无限制按钮可忽略</li>
 *   <li>红石控制：方块被红石信号激活（POWERED）→ 关闭（停止接收与转换）</li>
 *   <li>发光：工作中（有能量缓冲）时方块 LIT 发光</li>
 *   <li>默认绑定放置者 UUID（BlockEvent.EntityPlaceEvent 监听）</li>
 *   <li>每消耗 Config.ENERGY_PER_SKILL_POINT（1 亿）能量 → 给绑定玩家 +1 技能点</li>
 *   <li>进度条不可中断：一旦停止接收能量输入（超过 1 秒），进度清空重新计算</li>
 * </ul>
 */
public class SkillPointConverterBlockEntity extends BlockEntity implements MenuProvider {

    /** 能量缓冲（FE，long 64 位，上限 Long.MAX_VALUE；高频路径用 long 避免 BigInteger 分配开销） */
    private long progress = 0;
    /** 本机累计转换点（long 64 位） */
    private long totalConverted = 0;
    private long lastReceiveTick = -1;
    private UUID ownerUUID;
    /** GUI 无限制输入开关：true = 忽略输入速率限制（仍受缓冲上限约束） */
    private boolean unlimitedInput = false;
    /** 当前 tick 已接收能量计数（用于输入速率限制，每 tick 重置） */
    private long receivedThisTick = 0;
    private long lastResetTick = -1;
    /** 上次标记已变更的 tick（高频输入时合并 setChanged，避免每调用一次） */
    private long lastChangedTick = -1;
    /** 本机器输入速率上限（FE/t，GUI 可调；默认取 Config.MACHINE_MAX_INPUT_RATE） */
    private long inputRate = -1; // -1 = 未设置，使用 Config 默认

    /** 能量输入中断判定阈值：超过 1 秒（20 tick）未收到能量输入才清空进度 */
    private static final long PROGRESS_IDLE_TICKS = 20;

    /** 实际输入速率：机器自设值或 Config 默认 */
    private long effectiveInputRate() {
        return inputRate > 0 ? inputRate : Math.max(1, Config.MACHINE_MAX_INPUT_RATE.get());
    }

    public long getInputRate() {
        return effectiveInputRate();
    }

    public void setInputRate(long rate) {
        this.inputRate = Math.max(1, Math.min(rate, 1_000_000_000_000L)); // 1 ~ 1万亿
        setChanged();
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            long rate = effectiveInputRate();
            BlockPos pos = worldPosition;
            long converted = getPlayerConvertedPoints();
            return switch (index) {
                case 0 -> getProgressPercent();
                case 1 -> (int) (converted & 0xFFFFFFFFL); // 玩家整体累计转换低 32 位（64 位传输，突破 int 上限）
                case 2 -> ownerUUID != null ? 1 : 0;
                case 3 -> (int) Math.min(Integer.MAX_VALUE, getThreshold()); // 当前每点消耗（long clamp 到 int 显示）
                case 4 -> unlimitedInput ? 1 : 0; // GUI 无限制输入开关
                case 5 -> isRedstoneBlocked() ? 1 : 0; // 红石关闭状态
                case 6 -> (int) (rate & 0xFFFFFFFFL); // 本机器输入速率低 32 位（64 位传输，突破 int 上限）
                case 7 -> (int) (rate >>> 32);        // 本机器输入速率高 32 位
                case 8 -> pos.getX();                  // 机器方块位置 X
                case 9 -> pos.getY();                  // 机器方块位置 Y
                case 10 -> pos.getZ();                 // 机器方块位置 Z
                case 11 -> (int) (converted >>> 32);   // 玩家整体累计转换高 32 位
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 12;
        }
    };

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0 || isRedstoneBlocked()) {
                return 0;
            }
            Level lvl = SkillPointConverterBlockEntity.this.level;
            long gameTime = lvl != null ? lvl.getGameTime() : 0;
            // 每 tick 重置接收计数（输入速率限制）
            if (gameTime != lastResetTick) {
                receivedThisTick = 0;
                lastResetTick = gameTime;
            }
            long allowed = maxReceive;
            // 输入速率限制：每 tick 最多收 effectiveInputRate FE（GUI 无限制输入开关开启时忽略）
            if (!unlimitedInput) {
                long rate = effectiveInputRate();
                if (receivedThisTick >= rate) {
                    return 0; // 本 tick 已达输入上限
                }
                allowed = Math.min(allowed, rate - receivedThisTick);
            }
            // 缓冲上限约束（long 64 位）：progress 达 MACHINE_ENERGY_CAPACITY 后拒绝更多输入
            long capacity = Math.max(1, Config.MACHINE_ENERGY_CAPACITY.get());
            if (progress >= capacity) {
                return 0; // 缓冲已满
            }
            long accept = Math.min(allowed, capacity - progress);
            if (accept <= 0) {
                return 0;
            }
            if (!simulate) {
                if (lvl != null) {
                    SkillPointConverterBlockEntity.this.lastReceiveTick = gameTime;
                }
                receivedThisTick += accept;
                SkillPointConverterBlockEntity.this.progress += accept;
                // 高频输入（创造能量方块循环灌 60 万次/tick）：每 tick 仅标记一次已变更 + 刷新发光，避免每调用一次 setChanged 的性能开销
                if (gameTime != lastChangedTick) {
                    lastChangedTick = gameTime;
                    SkillPointConverterBlockEntity.this.setChanged();
                    SkillPointConverterBlockEntity.this.updateLitState(lvl);
                }
            }
            return (int) Math.min(Integer.MAX_VALUE, accept);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0; // 只进不出
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, progress);
        }

        @Override
        public int getMaxEnergyStored() {
            // 缓冲上限超过 int 时返回 int max（供线缆查询；实际缓冲为 long 无 int 限制）
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Config.MACHINE_ENERGY_CAPACITY.get()));
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return !isRedstoneBlocked();
        }
    };

    public SkillPointConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKILL_POINT_CONVERTER.get(), pos, state);
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

    public long getProgress() {
        return progress;
    }

    public long getTotalConverted() {
        return totalConverted;
    }

    /** GUI 无限制输入开关（true = 忽略输入速率限制，仍受缓冲上限约束） */
    public boolean isUnlimitedInput() {
        return unlimitedInput;
    }

    public void setUnlimitedInput(boolean unlimitedInput) {
        this.unlimitedInput = unlimitedInput;
        setChanged();
    }

    /** 是否被红石信号关闭 */
    public boolean isRedstoneBlocked() {
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(worldPosition);
        return state.hasProperty(SkillPointConverterBlock.POWERED) && state.getValue(SkillPointConverterBlock.POWERED);
    }

    /** 0-100 进度百分比（显示用） */
    public int getProgressPercent() {
        long threshold = getThreshold();
        if (threshold <= 0) {
            return 0;
        }
        return (int) Math.min(100, progress * 100 / threshold);
    }

    /**
     * 阶梯阈值：前 ENERGY_STEP_POINTS 点内，每点消耗从 ENERGY_START_COST 线性递增到 ENERGY_PER_SKILL_POINT；
     * 之后固定为最终消耗。
     * <p>⚠️ 2026-08-07 改为【玩家整体计算】：已转换点数取玩家全部机器累计（跨机器共享），
     * 不再是单台机器的 totalConverted——多台机器共享同一个阶梯进度，符合挂机多机玩法预期。
     */
    private long getThreshold() {
        long finalCost = Math.max(1, Config.ENERGY_PER_SKILL_POINT.get());
        long startCost = Math.max(1, Math.min(finalCost, Config.ENERGY_START_COST.get()));
        int step = Math.max(1, Config.ENERGY_STEP_POINTS.get());
        long converted = Math.min(getPlayerConvertedPoints(), step);
        long increment = (finalCost - startCost) / step; // 每点增量（线性递增）
        return startCost + converted * increment;
    }

    /**
     * 玩家整体累计转换的技能点数（阶梯消耗依据）。
     * 未绑定 owner 或服务端不可用时降级用本机累计（保持显示/计算不崩）。
     */
    private long getPlayerConvertedPoints() {
        if (ownerUUID == null || !(level instanceof ServerLevel serverLevel)) {
            return totalConverted;
        }
        PlayerSkillSavedData data = PlayerSkillSavedData.get(serverLevel);
        PlayerSkillRecord record = data.getOrCreatePlayer(ownerUUID);
        return record.getTotalConvertedPoints();
    }

    /** 由方块 getTicker 驱动的服务端每 tick 逻辑 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SkillPointConverterBlockEntity be) {
        if (level == null || level.isClientSide) {
            return;
        }
        long gameTime = level.getGameTime();
        // 红石关闭：不接收、不转换、清空进度（防止红石解锁后瞬间结算积压能量）
        if (be.isRedstoneBlocked()) {
            if (be.progress > 0) {
                be.progress = 0;
                be.setChanged();
            }
            be.updateLitState(level);
            return;
        }
        // 能量输入中断判定：超过 1 秒（20 tick）未收到能量输入才清空进度。
        // 原来 >1 tick 就清空，导致推送间隔 >2 tick 的能量源（线缆/发电机等）进度被反复清零，永远到不了 100%。
        if (be.lastReceiveTick < 0 || gameTime - be.lastReceiveTick > PROGRESS_IDLE_TICKS) {
            // 能量输入中断（或从未输入）→ 进度直接清空，重新计算
            if (be.progress > 0) {
                be.progress = 0;
                be.setChanged();
            }
            be.updateLitState(level);
            return;
        }
        long threshold = be.getThreshold();
        if (be.progress >= threshold) {
            long points = be.progress / threshold;
            be.progress %= threshold;
            be.totalConverted += points;
            be.grantSkillPoints(points); // long 64 位安全（无限制输入下单次转换可能超大）
            be.setChanged();
        }
        be.updateLitState(level);
    }

    /** 工作发光：有能量缓冲（接收中/未结算完）且未被红石关闭 → LIT=true；空闲 → LIT=false */
    private void updateLitState(Level lvl) {
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        boolean shouldLit = progress > 0 && !isRedstoneBlocked();
        BlockState current = lvl.getBlockState(worldPosition);
        if (current.hasProperty(SkillPointConverterBlock.LIT)
                && current.getValue(SkillPointConverterBlock.LIT) != shouldLit) {
            lvl.setBlock(worldPosition, current.setValue(SkillPointConverterBlock.LIT, shouldLit), 3);
        }
    }

    /** 给绑定玩家发放技能点（玩家离线也照常累计，符合挂机玩法） */
    private void grantSkillPoints(long amount) {
        if (ownerUUID == null || amount <= 0) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            PlayerSkillSavedData data = PlayerSkillSavedData.get(serverLevel);
            PlayerSkillRecord record = data.getOrCreatePlayer(ownerUUID);
            // 玩家整体累计转换量 +amount（阶梯消耗按玩家计算，跨机器共享；long 64 位防溢出）
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
        tag.putLong("Progress", progress);
        tag.putLong("TotalConverted", totalConverted);
        tag.putLong("LastReceiveTick", lastReceiveTick);
        tag.putBoolean("UnlimitedInput", unlimitedInput);
        tag.putLong("InputRate", inputRate);
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getLong("Progress");
        totalConverted = tag.getLong("TotalConverted");
        lastReceiveTick = tag.getLong("LastReceiveTick");
        unlimitedInput = tag.getBoolean("UnlimitedInput");
        inputRate = tag.getLong("InputRate");
        ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zifeng_s_custom_skill_tree.star_energy_converter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SkillPointConverterMenu(containerId, inventory, this);
    }
}
