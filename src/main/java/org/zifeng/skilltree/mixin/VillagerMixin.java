package org.zifeng.skilltree.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 村民大师（VILLAGER_MASTER，2026-08-27）：访问 AbstractVillager.updateTrades()。
 * <p>
 * ⚠️ 必须用 Mixin @Invoker：Forge 1.20.1 发布版运行时方法名是 SRG 名，
 * getDeclaredMethod("updateTrades") 找不到 → 交易配方不追加（村民满级但交易缺失）。
 * Mixin 编译期自动映射 SRG 名，运行时正确 invoke。
 */
@Mixin(net.minecraft.world.entity.npc.AbstractVillager.class)
public interface VillagerMixin {
    @Invoker("updateTrades")
    void zifeng$updateTrades();
}
