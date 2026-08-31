package org.zifeng.skilltree.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 万物挖掘（ULT_BREAK_ALL）：补"无法破坏方块"的掉落物（2026-09-01 最终版）。
 * <p>
 * ⚠️ 1.20.1 源码确认的根因：
 * <pre>
 *   // BlockStateBase.getDrops（srg m_287290_）——第一行就短路！
 *   public List&lt;ItemStack&gt; getDrops(LootParams.Builder builder) {
 *       if (this.lootTable == null) {
 *           return Collections.emptyList();   // ← 基岩/屏障/命令方块掉落表为 null → 直接返回空
 *       }
 *       return this.lootTable.getRandomItems(...);  // ← GLM（modifyLoot）只在这里执行
 *   }
 * </pre>
 * 基岩/屏障/命令方块等"无法破坏"方块没有原版掉落表（lootTable == null）→ 掉落列表恒空，
 * 且 <b>GLM 根本没有执行机会</b>（GLM 挂在 LootTable.getRandomItems → ForgeHooks.modifyLoot 里，
 * 基岩走不到）。这就是"能挖但无掉落"且 GLM 补不掉落分支是死代码的原因。
 * <p>
 * 正确注入点：{@link Block#getDrops(BlockState, ServerLevel, BlockPos, BlockEntity, Entity, ItemStack)}
 * 静态方法（srg m_49874_，Block.dropResources → playerDestroy 掉落生成的最终入口），
 * 返回空时补掉落。参数自带破坏者（entity）与工具（stack），判断齐全。
 * <p>
 * 条件：掉落列表为空 + 方块 destroySpeed &lt; 0（不可破坏类）+ 破坏者是真/假玩家 +
 * canBreakUnbreakable（技能开启+持镐）→ 补该方块自身物品（asItem）。
 * 补完后再走挪移（LootVacuumEvents）→ 与普通方块行为一致（有绑定容器直接进容器）。
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {

    // ⚠️ getDrops 有两个重载（srg：m_49869_ 4参=合成路径 / m_49874_ 6参=玩家破坏路径）：
    // 必须用完整描述符锁定 6 参版本（带 Entity + ItemStack，破坏者/工具齐全），
    // 否则 Mixin APT 会匹配到 4 参（合成路径，无玩家信息 → 补掉落永远不触发）。
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)"
            + "Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private static void zifeng$fillUnbreakableDrops(BlockState state, ServerLevel level, BlockPos pos,
                                                    BlockEntity blockEntity, Entity entity, ItemStack stack,
                                                    CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> original = cir.getReturnValue();
        if (original != null && !original.isEmpty()) {
            return; // 原版有掉落（普通方块）：不干预
        }
        // 只处理"无法破坏"方块（基岩/屏障/命令方块等 destroySpeed < 0）
        if (state.getDestroySpeed(level, pos) >= 0.0F) {
            return;
        }
        if (!(entity instanceof ServerPlayer sp)) {
            return; // 只对玩家破坏者补掉落
        }
        if (!org.zifeng.skilltree.event.UltimateEvents.canBreakUnbreakable(sp)) {
            return; // 技能未开启或未持镐
        }
        Item blockItem = state.getBlock().asItem();
        if (blockItem == null || blockItem == Items.AIR) {
            return; // 无物品形式（末地传送门/结构空位等）：不补
        }
        // 补该方块自身物品
        List<ItemStack> drops = new ArrayList<>(original == null ? List.of() : original);
        drops.add(new ItemStack(blockItem, 1));

        // 挪移兼容：有绑定容器 → 直接进容器（与普通方块掉落行为一致）
        org.zifeng.skilltree.data.PlayerSkillRecord record = org.zifeng.skilltree.event.UltimateEvents.getRecordFor(sp);
        if (org.zifeng.skilltree.event.LootVacuumEvents.hasBinding(sp, record)) {
            boolean vacuumed = org.zifeng.skilltree.event.LootVacuumEvents.tryVacuumDropsStacks(sp, record, drops);
            if (vacuumed) {
                drops.clear();
            }
        }
        cir.setReturnValue(drops);
    }
}
