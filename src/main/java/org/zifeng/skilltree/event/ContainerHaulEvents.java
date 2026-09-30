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
     * 遍历打开的菜单槽位：跳过玩家背包槽（container == player 背包）与 AE2 设备槽位，
     * 其余视为外部容器槽，把物品逐格 insertIntoBoundRaw 塞进绑定容器。
     *
     * <p>★★ 2026-09-30 重写（修「搬运精妙容器会随机销毁物品」，双版本）：
     * <b>不再拿 slot.getItem() 的活引用去插入，也不再用 slot.set() 清源槽。</b>
     *
     * <p><b>根因（Forge/NeoForge 源码实证，非推测）：</b>
     * <ol>
     *   <li>{@code SlotItemHandler.getItem()} = {@code itemHandler.getStackInSlot(index)} ——
     *       返回的是容器内部列表里的<b>那个对象本身</b>，不是副本；</li>
     *   <li>{@code ItemStackHandler.insertItem()} 在目标槽为空时执行
     *       {@code stacks.set(slot, stack)} —— <b>把传进来的那个对象原样存下</b>
     *       （只有被 limit 截断时才 copyWithCount）。</li>
     * </ol>
     * 两者相加 → 源容器与目标容器<b>共享同一个 ItemStack 对象</b>：目标容器后续任何
     * 数量/内容改动都会连带改到源容器，我们再 set 写回源槽时数量已经错乱 → 随机丢物。
     * <p>只有「活引用读取 + 回写源槽」的搬运术会踩到；掉落直传与选区拆解只往目标里插、
     * 从不回写源槽，所以哪怕是巨量物品也一直正常（与实测一致）。
     *
     * <p><b>修法：</b>
     * <ol>
     *   <li>读出来立刻 {@code copy()}，用副本去插入 → 源、目标永不共享对象；</li>
     *   <li>清源槽改用<b>官方移除管线</b> {@code slot.remove(n)}
     *       （容器自己的 extractItem / removeItem，不绕过其过滤与升级响应），
     *       且只按<b>实际搬走的数量</b>精确扣除。</li>
     * </ol>
     * 因为「插进去多少才取多少」，即便源与目标恰好是同一个容器（自搬），
     * 净效果也是 0（取 N → 放回 N → 再取 N，总量不变），
     * 不会再有旧实现「先 set(EMPTY) 把刚合并进去的物品一起抹掉」的破坏。
     */
    private static void transferFromMenu(ServerPlayer player, PlayerSkillRecord record, AbstractContainerMenu menu) {
        if (menu == null || player == null) {
            return;
        }
        boolean anyMoved = false;
        // 快照槽位列表：取出物品时源容器可能改结构，不再直接遍历 menu.slots 视图
        final java.util.List<Slot> slots = new java.util.ArrayList<>(menu.slots);
        for (Slot slot : slots) {
            if (slot == null) {
                continue;
            }
            // 跳过玩家背包槽（主物品栏/快捷栏/盔甲/副手都属于 player 的背包 Container）
            if (slot.container == player.getInventory()) {
                continue;
            }
            // ⚠️ 2026-09-12（1.4.1）跳过 AE2 设备槽位：驱动器里的存储元件、设备上的升级卡、
            //    接口/样板供应器的配置与样板——这些是「设备自身零件」，不是可搬运的容器内容。
            //    否则打开 AE 驱动器 GUI 时，元件会被搬进绑定网络并清空源槽（元件直接消失）。
            if (isAeDeviceSlot(slot)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            // ★ 2026-09-30：必须用副本 —— 目标容器空槽时会直接把传入对象存下，
            //   把源容器内部对象交出去会造成两个容器共享同一对象（见方法说明）
            ItemStack work = stack.copy();
            ItemStack leftover = LootVacuumEvents.insertIntoBoundRaw(player, record, work);
            int moved = work.getCount() - leftover.getCount();
            if (moved > 0) {
                takeFromSourceSlot(slot, moved);
                anyMoved = true;
            }
        }
        if (anyMoved) {
            player.containerMenu.broadcastChanges();
        }
    }

    /**
     * 从源槽按「实际搬走的数量」精确取出（★ 2026-09-30 新增）。
     *
     * <p><b>为何不用 {@code slot.set(...)}：</b>{@code set} 是「整格覆盖写」，
     * 对精妙这类 {@code SlotItemHandler} 会直接 {@code setStackInSlot} 并触发完整槽变更管线，
     * 但覆盖写要由我们自己算数量 —— 一旦数量算错（对象共享、大栈、合成数量）就会写坏槽。
     * 用官方取出 API 则由容器自己决定扣多少，天然正确。
     *
     * <p><b>为何要循环：</b>单次取出可能被容器接口的 maxStackSize 截断
     * （精妙 {@code extractItemInternal} 是 {@code min(amount, existing.getMaxStackSize())}，
     * 超大栈一次只出 64）。循环取到取够为止；某次取不出东西（返回 0）立即停止，防死循环。
     */
    private static void takeFromSourceSlot(Slot slot, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack taken = slot.remove(remaining);
            int takenCount = taken == null ? 0 : taken.getCount();
            if (takenCount <= 0) {
                return; // 取不动了（已空 / 容器拒绝）→ 停止，避免死循环
            }
            remaining -= takenCount;
        }
    }

    /**
     * 是否为 AE2 设备槽位（2026-09-12 1.4.1 新增）。
     *
     * <p><b>要解决的问题：</b>搬运术原本把菜单里除玩家背包外的所有槽位都当成"外部容器物品"，
     * 于是打开 AE2 驱动器 GUI 时，槽里的**存储元件**被搬进绑定网络并 `slot.set(EMPTY)` 清空→
     * 元件直接消失（空元件尤其明显：AE2 元件对已存类型会自动锁定，空元件不锁定所以会接受
     * "元件"这个新类型而插入成功；有内容的元件因类型锁定而拒绝，反而侥幸没被搬走）。
     *
     * <p><b>判定依据（javap 实测 AE2 19.2.17 / 15.4.10）：</b>AE2 设备槽位全部继承
     * {@code appeng.menu.slot.AppEngSlot}（`RestrictedInputSlot` / `FakeSlot` / `OutputSlot` /
     * `CellPartitionSlot` 等），均位于 {@code appeng.menu.slot} 包下；
     * 另部分槽（如 ME 终端的网络物品视图）用原版 {@code Slot} 但背后容器是 AE2 内部容器。
     * 因此用<b>包名前缀</b>判定（纯反射、零依赖，且不会误伤其他模组的普通容器槽）。
     */
    private static boolean isAeDeviceSlot(Slot slot) {
        try {
            // ① 槽位类本身是 AE2 的（appeng.menu.slot.RestrictedInputSlot 等）
            if (slot.getClass().getName().startsWith("appeng.")) {
                return true;
            }
            // ② 槽位背后的容器是 AE2 内部容器（ME 终端网络物品视图槽用原版 Slot）
            net.minecraft.world.Container c = slot.container;
            return c != null && c.getClass().getName().startsWith("appeng.");
        } catch (Throwable t) {
            return false; // 判定失败时按普通槽处理（保守：不阻止搬运）
        }
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : java.util.UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
