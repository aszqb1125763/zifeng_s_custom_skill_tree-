package org.zifeng.skilltree.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.event.LootVacuumEvents;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 方块掉落修改器（Global Loot Modifier，1.20.1 Forge 原生机制，2026-09-01 重构）：
 * <p>
 * ✅ 替代旧的 BreakEvent 取消方案（三个 bug：箱子物品消失/双重音效/Mek 不耗能——
 *    根因：取消破坏后 playerWillDestroy 不执行 + 手动 setBlock 绕过原版流程）。
 * <p>
 * GLM 在【掉落物生成后、变成实体前】修改掉落列表——不取消破坏流程：
 * 容器物品（playerWillDestroy）/ 音效 / Mek 能量 全部走原版正常，同时技能效果生效。
 * <p>
 * 处理顺序（与 1.21.1 BlockDropsEvent 语义一致）：
 * 万物挖掘补掉落 → 自动熔炼 → 点石成金倍率 → 凋落物挪移（传送绑定容器）
 * <p>
 * 参考：TinkersConstruct（SlimeKnights，MIT）的 LootModifier 方案（自动熔炼/掉落修改）。
 */
public class SkillTreeLootModifier extends LootModifier {

    public static final Codec<SkillTreeLootModifier> CODEC = RecordCodecBuilder.create(inst ->
            codecStart(inst).apply(inst, SkillTreeLootModifier::new));

    protected SkillTreeLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Nonnull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // 只处理方块掉落（方块破坏产生的掉落才有 BLOCK_STATE）
        if (!context.hasParam(LootContextParams.BLOCK_STATE)) {
            return generatedLoot;
        }
        // 1.20.1 LootContext.getLevel() 直接返回 ServerLevel（方块掉落只会发生在服务端）
        ServerLevel serverLevel = context.getLevel();
        // 破坏者（玩家/假玩家）：必须有手持工具 + 破坏者是玩家
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool == null) {
            return generatedLoot;
        }
        if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer sp)) {
            return generatedLoot;
        }
        if (sp.isCreative()) {
            return generatedLoot;
        }
        PlayerSkillRecord record = org.zifeng.skilltree.data.PlayerSkillSavedData
                .get(serverLevel).getOrCreatePlayer(sp.getUUID());
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null) {
            return generatedLoot;
        }
        net.minecraft.core.BlockPos pos = null;
        net.minecraft.world.phys.Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin != null) {
            pos = net.minecraft.core.BlockPos.containing(origin);
        }

        // 机械共鸣/真玩家判断（与 1.21.1 BlockDropsEvent 语义一致）
        boolean autoSmeltOn = sp instanceof net.minecraftforge.common.util.FakePlayer
                ? SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_AUTO_SMELT)
                : (record.getLearnedPoints(Skills.AUTO_SMELT) > 0 && record.isEnabled(Skills.AUTO_SMELT));
        double mult = sp instanceof net.minecraftforge.common.util.FakePlayer
                ? (SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_BLOCK_DROP)
                    ? SkillEffects.getBlockDropMultiplier(record) : 1.0)
                : (record.getLearnedPoints(Skills.BLOCK_DROP) > 0 && record.isEnabled(Skills.BLOCK_DROP)
                    ? SkillEffects.getBlockDropMultiplier(record) : 1.0);
        boolean vacuumPossible = LootVacuumEvents.hasBinding(sp, record);

        // 无任何相关技能/效果 → 走原版掉落
        // （万物挖掘补掉落已迁移至 BlockDropsMixin：基岩类掉落表为 null，GLM 根本不执行）
        if (!autoSmeltOn && mult <= 1.0 && !vacuumPossible) {
            return generatedLoot;
        }

        // 转可变列表（原版生成的是可变 ObjectArrayList，这里再包一层安全）
        List<ItemStack> drops = new ArrayList<>(generatedLoot);

        // 自动熔炼（终极节点）：先熔炉 → 再时运（原版已含）→ 再技能增幅（倍率）
        if (autoSmeltOn) {
            UltimateEvents.applyAutoSmelt(sp, drops, record);
        }
        // 点石成金（方块掉落倍率）：仅对掉落表含"时运"的方块生效
        if (mult > 1.0) {
            net.minecraft.resources.ResourceLocation lootKey = state.getBlock().getLootTable();
            if (lootKey != null && UltimateEvents.supportsFortune(lootKey, serverLevel)) {
                UltimateEvents.applyDropMultiplierStacks(drops, sp, mult);
            }
        }
        // 凋落物挪移（光环技能）：掉落物直传绑定容器，不生成实体（防卡顿）
        if (vacuumPossible) {
            boolean vacuumed = LootVacuumEvents.tryVacuumDropsStacks(sp, record, drops);
            if (vacuumed) {
                drops.clear();
            }
        }
        // 回写结果
        generatedLoot.clear();
        generatedLoot.addAll(drops);
        return generatedLoot;
    }

    @Override
    public Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier> codec() {
        return CODEC;
    }
}
