package org.zifeng.skilltree.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.zifeng.skilltree.blockentity.StarEnergyConverterBlockEntity;
import org.zifeng.skilltree.init.ModMenus;

/**
 * 星能转换机信息界面（无槽位，纯进度展示）。
 * ContainerData：0=进度百分比 1=已转换技能点 2=是否已绑定
 * 3=当前每点消耗 4=输入速率低32位 5=输入速率高32位 6=红石控制开关 7=能量上限
 */
public class StarEnergyConverterMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private BlockPos blockPos;

    public StarEnergyConverterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainerData(8));
    }

    public StarEnergyConverterMenu(int containerId, Inventory inventory, StarEnergyConverterBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity.getContainerData());
        this.blockPos = blockEntity.getBlockPos();
    }

    public StarEnergyConverterMenu(int containerId, Inventory inventory, ContainerData data) {
        super(ModMenus.STAR_ENERGY_CONVERTER.get(), containerId);
        this.data = data;
        addDataSlots(data);
    }

    /** 方块实体位置（客户端发送速率设置包用） */
    public BlockPos getBlockPos() {
        return blockPos;
    }

    public int getProgressPercent() {
        return data.get(0);
    }

    public int getTotalConverted() {
        return data.get(1);
    }

    public boolean isBound() {
        return data.get(2) == 1;
    }

    /** 当前每点技能点消耗（FE，阶梯制实时值） */
    public int getCurrentCost() {
        return data.get(3);
    }

    /** 当前输入速率（每 tick FE，由低/高 32 位两槽拼出完整 long） */
    public long getInputRatePerTick() {
        return StarEnergyConverterBlockEntity.combineLong(data.get(4), data.get(5));
    }

    /** 红石控制模式是否开启 */
    public boolean isRedstoneControlled() {
        return data.get(6) == 1;
    }

    /** 能量上限（Long 范围；data 为 int，仅用于展示近似值） */
    public long getEnergyCapacity() {
        int v = data.get(7);
        return v < 0 ? Long.MAX_VALUE : Integer.toUnsignedLong(v);
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
