package org.zifeng.skilltree.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.util.function.Consumer;

/**
 * 采掘熟稔：工具耐久损耗减免（原版无耐久事件，用 Mixin 拦截伤害计算）。
 * ⚠️ 1.20.1 方案（比 1.21.1 的 @ModifyVariable 更稳）：
 *    ItemStack.hurtAndBreak(int, T, Consumer) 内部调用
 *    Item.damageItem(ItemStack, int, LivingEntity, Consumer) -> int（Forge 1.20.1 新增，
 *    返回实际耐久消耗）。
 *    用 @Redirect 拦截该调用，直接返回减免后的耐久消耗值——
 *    @Redirect 的 handler 签名 = (被重定向方法的 receiver + 全部参数)，
 *    能拿到 entity（判断玩家/技能），且在 dev（official）与发布（srg）字节码签名一致，无签名错配问题。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Redirect(
            method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/Item;damageItem(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)I"),
            require = 0
    )
    private int zifeng$reduceDurabilityDamage(Item item, ItemStack stack, int amount,
                                              LivingEntity entity, Consumer<LivingEntity> onBroken) {
        if (amount > 0 && entity instanceof ServerPlayer player) {
            PlayerSkillRecord record = PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
            // ⚠️ 机械共鸣：假玩家（机器）需学习并开启 工具不毁·共鸣 才继承耐久减免
            if (SkillEffects.isEffectAllowedFor(player, record, Skills.MACHINE_UNBREAKABLE)) {
                double reduction = SkillEffects.getToolDurabilityReduction(record);
                if (reduction > 0) {
                    int reduced = (int) Math.floor(amount * (1 - Math.min(1.0, reduction)));
                    return Math.max(0, reduced);
                }
            }
        }
        return item.damageItem(stack, amount, entity, onBroken);
    }
}
