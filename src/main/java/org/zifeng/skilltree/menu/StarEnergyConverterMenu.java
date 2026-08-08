package org.zifeng.skilltree.menu;

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
 */
public class StarEnergyConverterMenu extends AbstractContainerMenu {
    private final ContainerData data;

    public StarEnergyConverterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainerData(4));
    }

    public StarEnergyConverterMenu(int containerId, Inventory inventory, StarEnergyConverterBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity.getContainerData());
    }

    public StarEnergyConverterMenu(int containerId, Inventory inventory, ContainerData data) {
        super(ModMenus.STAR_ENERGY_CONVERTER.get(), containerId);
        this.data = data;
        addDataSlots(data);
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
