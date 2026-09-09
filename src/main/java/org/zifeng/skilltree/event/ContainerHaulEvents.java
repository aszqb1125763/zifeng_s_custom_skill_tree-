package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.Skills;

/**
 * 子枫的搬运术（CONTAINER_HAUL，2026-09-07）：
 * 把「玩家打开的容器」内的物品全部转移进「绑定容器」（绑定容器由子枫挪移术/搬运术
 * 共用——手持木棍潜行右键绑定，数据存玩家存档 LootVacuum* 字段，独立子功能）。
 * <p>两种模式（存 auraTargetModes[container_haul]，0=自动 1=手动）：
 * <ul>
 *   <li>自动（0）：打开容器瞬间自动转移所有物品</li>
 *   <li>手动（1）：打开容器后按「功能触发键」手动转移（第三类快捷键，容器 GUI 打开时也可按）</li>
 * </ul>
 * <p>转移源 = 玩家当前打开的菜单中【非玩家背包】的槽位（外部容器槽）。
 * 转移目标 = 绑定容器（insertIntoBoundRaw：仅要求绑定存在，不要求挪移术开启）。
 */
public final class ContainerHaulEvents {
    private ContainerHaulEvents() {
    }

    /** 搬运术模式常量（auraTargetModes 复用，clamp 0-1） */
    public static final int MODE_AUTO = 0;  // 开箱自动转移
    public static final int MODE_MANUAL = 1; // 按键手动转移

    /** 技能已学且开启 */
    private static boolean isSkillActive(PlayerSkillRecord record) {
        return record != null
                && record.getLearnedPoints(Skills.CONTAINER_HAUL) > 0
                && record.isEnabled(Skills.CONTAINER_HAUL);
    }

    // ============ 自动模式：打开容器瞬间转移 ============

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        if (!isSkillActive(record)) {
            return;
        }
        if (record.getAuraTargetMode(Skills.CONTAINER_HAUL) != MODE_AUTO) {
            return; // 仅自动模式
        }
        AbstractContainerMenu menu = event.getContainer();
        if (isMenuTheBoundContainer(menu, record)) {
            return; // ⚠️ 打开的就是绑定容器自身：自搬会把物品从自身槽抹掉（2026-09-07）
        }
        transferFromMenu(player, record, menu);
    }

    // ============ 手动模式：功能触发键调用（ContainerHaulC2SPacket → 这里） ============

    /** 手动触发一次：把玩家当前打开的容器物品搬进绑定容器（无反应要求=无容器/无技能/非手动模式时静默） */
    public static void triggerManual(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerSkillRecord record = getRecord(player);
        if (!isSkillActive(record)) {
            return; // 未学/关闭 → 无反应
        }
        // ⚠️ 2026-09-07 三级闸门：子3级（触发）需模式=手动（服务端权威校验，防客户端直发）
        if (record.getAuraTargetMode(Skills.CONTAINER_HAUL) != MODE_MANUAL) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) {
            return; // 没打开外部容器（自己背包不算）→ 无反应
        }
        if (isMenuTheBoundContainer(menu, record)) {
            return; // ⚠️ 打开的就是绑定容器自身：自搬会清空（2026-09-07）
        }
        transferFromMenu(player, record, menu);
    }

    // ============ 核心：把菜单中的外部容器槽位物品搬进绑定容器 ============

    /**
     * 判断打开的菜单其源容器是否就是玩家绑定的容器（2026-09-07）。
     * ⚠️ 若打开的就是绑定容器，转移 = 自搬：物品从自身槽取出→插回同一容器
     *（insertIntoBoundRaw 目标=源），同槽合并后 slot.set(EMPTY) 会把物品抹掉 → 绑定容器被清空。
     * 覆盖三类：①菜单外部槽 container 是 BlockEntity 且位置==绑定；②Sophisticated 存储菜单
     *（槽 container 是内部 InventoryHandler 非 BE，但其菜单能取 storageBlockEntity 位置）；
     * ③玩家背包槽的 container 是 Inventory（非 BlockEntity），自动跳过。
     */
    private static boolean isMenuTheBoundContainer(AbstractContainerMenu menu, PlayerSkillRecord record) {
        if (menu == null || record == null || !record.hasLootVacuumBind()) {
            return false;
        }
        // ② Sophisticated 存储菜单（精妙箱子/桶）：菜单可直接取存储方块位置（官方 getStorageBlockEntity）
        net.minecraft.core.BlockPos menuPos = org.zifeng.skilltree.compat.SophisticatedCompat.storageMenuPos(menu);
        if (menuPos != null
                && menuPos.getX() == record.getLootVacuumX()
                && menuPos.getY() == record.getLootVacuumY()
                && menuPos.getZ() == record.getLootVacuumZ()) {
            return true;
        }
        // ① 通用：菜单外部槽 container 是方块实体且位置==绑定
        for (Slot slot : menu.slots) {
            net.minecraft.world.Container c = slot.container;
            if (c instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                net.minecraft.world.level.Level lvl = be.getLevel();
                if (lvl != null
                        && record.getLootVacuumDim().equals(lvl.dimension().location().toString())
                        && be.getBlockPos().getX() == record.getLootVacuumX()
                        && be.getBlockPos().getY() == record.getLootVacuumY()
                        && be.getBlockPos().getZ() == record.getLootVacuumZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 遍历打开的菜单槽位：跳过玩家背包槽（container == player 背包），其余视为外部容器槽，
     * 把物品逐格 insertIntoBoundRaw 塞进绑定容器。塞完的空槽 set 回空。
     */
    private static void transferFromMenu(ServerPlayer player, PlayerSkillRecord record, AbstractContainerMenu menu) {
        if (menu == null || player == null) {
            return;
        }
        boolean anyMoved = false;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot == null) {
                continue;
            }
            // 跳过玩家背包槽（主物品栏/快捷栏/盔甲/副手都属于 player 的背包 Container）
            if (slot.container == player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack leftover = LootVacuumEvents.insertIntoBoundRaw(player, record, stack);
            if (leftover.isEmpty()) {
                slot.set(ItemStack.EMPTY); // 全部搬走 → 清空源格
                anyMoved = true;
            } else if (leftover.getCount() != stack.getCount()) {
                slot.set(leftover); // 部分搬走 → 剩余放回源格
                anyMoved = true;
            }
        }
        if (anyMoved) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : java.util.UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
