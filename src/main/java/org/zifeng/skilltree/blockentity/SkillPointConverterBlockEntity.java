package org.zifeng.skilltree.blockentity;


import net.minecraft.core.BlockPos;
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
import net.minecraftforge.energy.IEnergyStorage;
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
    /** 转换速率快照基准（每 2 秒计算 totalConverted 增量，2026-08-25） */
    private long lastRateTotal = 0;

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

    /** 1.20.1 Forge capability：暴露 FE 能量存储（IEnergyStorage）+ Flux long 能量（可选） */
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap, net.minecraft.core.Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> energyStorage).cast();
        }
        // Flux-Networks long 能量（可选软集成）：Flux 灌 long 能量突破 int 上限
        net.minecraftforge.common.util.LazyOptional<Object> flux =
                org.zifeng.skilltree.compat.FluxCompat.getFluxStorage(cap, this);
        if (flux != null) {
            return flux.cast();
        }
        return super.getCapability(cap, side);
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

    /** 能量缓冲上限（long 64 位，Flux long 接口用） */
    public long getCapacityLong() {
        return Math.max(1, Config.MACHINE_ENERGY_CAPACITY.get());
    }

    /**
     * Flux-Networks long 能量接收（2026-08-25）：Flux 的 IFNEnergyStorage.receiveEnergyL(long)，
     * 突破普通 IEnergyStorage 的 int（21 亿）上限，直接灌 long 能量。
     * 复用 receiveEnergy 的接收逻辑（输入速率限制/缓冲上限/进度累积），但接受 long 量。
     */
    public long receiveEnergyLong(long maxReceive, boolean simulate) {
        if (maxReceive <= 0 || isRedstoneBlocked()) {
            return 0;
        }
        Level lvl = level;
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
                return 0;
            }
            allowed = Math.min(allowed, rate - receivedThisTick);
        }
        // 缓冲上限约束（long 64 位）：progress 达 capacity 后拒绝更多输入
        long capacity = getCapacityLong();
        if (progress >= capacity) {
            return 0;
        }
        long accept = Math.min(allowed, capacity - progress);
        if (accept <= 0) {
            return 0;
        }
        if (!simulate) {
            if (lvl != null) {
                lastReceiveTick = gameTime;
            }
            receivedThisTick += accept;
            progress += accept;
            if (gameTime != lastChangedTick) {
                lastChangedTick = gameTime;
                setChanged();
                updateLitState(lvl);
            }
        }
        return accept;
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
     * 阶梯阈值：当前每点消耗（FE）。
     * ⚠️ 2026-08-28 架构升级：折扣计算收进玩家子系统（PlayerPushState.getCurrentCostPerPoint），
     * 前 {@link Config#ENERGY_STEP_POINTS}（2000）点是【玩家所有机器合计】的共享折扣——
     * 防"每台机器各有 2000 点折扣"漏洞；机器只查询结果，不各自计算。
     * 未绑定 owner 时降级用本机累计计算（保持显示/计算不崩）。
     * <p>⚠️ 2026-08-27 性能优化：阶梯成本变化缓慢（基于玩家累计转换点），每 20 tick 缓存一次。
     */
    private long cachedThreshold = -1;
    private long cachedThresholdTick = -1;

    private long getThreshold() {
        long gameTime = level != null ? level.getGameTime() : 0;
        if (cachedThreshold >= 0 && gameTime - cachedThresholdTick < 20) {
            return cachedThreshold;
        }
        long result;
        if (ownerUUID == null || !(level instanceof ServerLevel serverLevel)) {
            // 未绑定 owner：本机累计降级计算
            long finalCost = Math.max(1, Config.ENERGY_PER_SKILL_POINT.get());
            long startCost = Math.max(1, Math.min(finalCost, Config.ENERGY_START_COST.get()));
            int step = Math.max(1, Config.ENERGY_STEP_POINTS.get());
            long converted = Math.min(totalConverted, step);
            long increment = (finalCost - startCost) / step;
            result = startCost + converted * increment;
        } else {
            // 绑定 owner：玩家子系统统一折扣（所有机器共享同一档位）
            result = org.zifeng.skilltree.PlayerPushState.get(ownerUUID).getCurrentCostPerPoint(serverLevel);
        }
        cachedThreshold = result;
        cachedThresholdTick = gameTime;
        return result;
    }

    /**
     * 玩家整体累计转换的技能点数（阶梯消耗依据，前 2000 点打折进度）。
     * ⚠️ 2026-08-28 架构升级：读玩家子系统共享缓存（20 tick 刷新），
     * 挂机 20 台机器 = 每 tick 1 次缓存读取（原来每台机器都查 SavedData）。
     * 未绑定 owner 或服务端不可用时降级用本机累计（保持显示/计算不崩）。
     */
    private long getPlayerConvertedPoints() {
        if (ownerUUID == null || !(level instanceof ServerLevel serverLevel)) {
            return totalConverted;
        }
        return org.zifeng.skilltree.PlayerPushState.get(ownerUUID).getConvertedPoints(serverLevel);
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
        // ⚠️ 2026-08-25：转换机每 2 秒（40 tick）快照一次转换速率发给 owner（/秒，恒定通电用速率显示）
        if (gameTime % 40 == 0 && be.ownerUUID != null) {
            net.minecraft.server.level.ServerPlayer owner = level.getServer().getPlayerList().getPlayer(be.ownerUUID);
            if (owner != null) {
                // 本 2 秒窗口内转换的技能点数（totalConverted 增量）
                long nowConverted = be.totalConverted;
                long delta = nowConverted - be.lastRateTotal;
                be.lastRateTotal = nowConverted;
                if (delta > 0) {
                    double perSec = delta / 2.0; // 每 2 秒窗口 → 每秒速率
                    java.util.Map<String, Double> rates = new java.util.HashMap<>();
                    rates.put("converter:", perSec);
                    org.zifeng.skilltree.network.ModNetwork.sendToPlayer(owner,
                            new org.zifeng.skilltree.network.SkillPointRateS2CPacket(
                                    org.zifeng.skilltree.data.PlayerSkillSavedData.get((net.minecraft.server.level.ServerLevel) level)
                                            .getOrCreatePlayer(be.ownerUUID).getSkillPoints(), rates));
                } else {
                    be.lastRateTotal = nowConverted;
                }
            }
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

    /** 给绑定玩家发放技能点（玩家离线也照常累计，符合挂机玩法）
     *  ⚠️ 2026-08-28 架构升级：走玩家子系统增量推送（SkillPointDeltaS2CPacket 16 字节），
     *     替代原来每 10 tick 全量重发 SkillTreeDataS2CPacket（多人带宽优化 + 防数据串）。 */
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
            // ⚠️ 子系统联动（2026-08-28）：
            //  ① 阶梯进度缓存失效（下一 tick 机器 getThreshold 自动重读玩家整体进度）
            //  ② 技能点增量合并推送（每 10 tick 一个 16 字节增量包，替代全量）
            org.zifeng.skilltree.PlayerPushState push = org.zifeng.skilltree.PlayerPushState.get(ownerUUID);
            push.invalidateConvertedCache();
            push.addSkillPointDelta(granted, serverLevel);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
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
    public void load(CompoundTag tag) {
        super.load(tag);
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
