package org.zifeng.skilltree.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.util.function.Consumer;

/**
 * 采掘熟稔：工具耐久损耗减免（原版无耐久事件，用 Mixin 修改 hurtAndBreak 的损耗量）。
 * 仅当使用者是玩家且拥有"采掘熟稔"技能时生效。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @ModifyVariable(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0
    )
    private int zifeng$reduceDurabilityDamage(int amount, int ignored, ServerLevel level, LivingEntity entity, Consumer<Item> consumer) {
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
        return amount;
    }
}
