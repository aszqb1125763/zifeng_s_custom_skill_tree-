package org.zifeng.skilltree.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.init.ModMenus;

/**
 * 技能点转换机信息界面（无槽位，纯进度展示）。
 * ContainerData：
 *   0=进度百分比 1=已转换技能点 2=是否已绑定 3=当前每点消耗 4=无限制输入开关 5=红石关闭状态
 *   6/7=输入速率(64位高低32位) 8/9/10=机器BlockPos
 */
public class SkillPointConverterMenu extends AbstractContainerMenu {
    private final ContainerData data;
    /** 服务端持有真实 BlockEntity 引用（C2S 包直接用它，不依赖 BlockPos 同步）；客户端为 null */
    private final SkillPointConverterBlockEntity blockEntity;

    public SkillPointConverterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainerData(12), null);
    }

    public SkillPointConverterMenu(int containerId, Inventory inventory, SkillPointConverterBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity.getContainerData(), blockEntity);
    }

    public SkillPointConverterMenu(int containerId, Inventory inventory, ContainerData data, SkillPointConverterBlockEntity blockEntity) {
        super(ModMenus.SKILL_POINT_CONVERTER.get(), containerId);
        this.data = data;
        this.blockEntity = blockEntity;
        addDataSlots(data);
    }

    /** 服务端返回真实机器引用（C2S 包用它操作）；客户端返回 null */
    public SkillPointConverterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** 机器方块位置：从同步数据槽恢复（客户端发 C2S 包用，服务端兜底） */
    public BlockPos getBlockPos() {
        return new BlockPos(data.get(8), data.get(9), data.get(10));
    }

    public int getProgressPercent() {
        return data.get(0);
    }

    /** 玩家累计已转换技能点（64 位，从高/低 32 位还原，突破 int 上限） */
    public long getTotalConverted() {
        return ((long) data.get(11) << 32) | ((long) data.get(1) & 0xFFFFFFFFL);
    }

    public boolean isBound() {
        return data.get(2) == 1;
    }

    /** 当前每点技能点消耗（FE，阶梯制实时值） */
    public int getCurrentCost() {
        return data.get(3);
    }

    /** GUI 无限制输入开关（true = 忽略输入速率限制） */
    public boolean isUnlimitedInput() {
        return data.get(4) == 1;
    }

    /** 是否被红石信号关闭 */
    public boolean isRedstoneBlocked() {
        return data.get(5) == 1;
    }

    /** 本机器输入速率（FE/t，64 位，从高/低 32 位还原；为 0 时用 Config 默认） */
    public long getInputRate() {
        long rate = ((long) data.get(7) << 32) | ((long) data.get(6) & 0xFFFFFFFFL);
        if (rate <= 0) {
            return Math.max(1, org.zifeng.skilltree.Config.MACHINE_MAX_INPUT_RATE.get());
        }
        return rate;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 信息展示界面（无槽位交互），保持一直有效；原版容器会检查距离，此处无需
        return true;
    }
}
