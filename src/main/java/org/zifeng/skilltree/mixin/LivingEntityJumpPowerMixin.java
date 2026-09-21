package org.zifeng.skilltree.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 1.20.1 的跳跃真正受 JUMP_STRENGTH 属性影响
 * （★ 2026-09-20，用户报「跳跃高度两个技能没用，要修复」）。
 *
 * <h2>根因（反编译字节码确认，非推测）</h2>
 * <pre>
 *   1.20.1  LivingEntity.getJumpPower()
 *           = 0.42f * getBlockJumpFactor() + getJumpBoostPower()
 *             ↑ 硬编码常量，【完全不读】JUMP_STRENGTH 属性 —— 所以技能加了属性也不影响跳跃
 *
 *   1.21.1  LivingEntity.getJumpPower(float mul)
 *           = getAttributeValue(JUMP_STRENGTH) * mul * getBlockJumpFactor()
 *             + getJumpBoostPower()      ← 读属性，所以 1.21.1 一直正常
 * </pre>
 * 症状：1.20.1 里「跃升体术（跳跃强化）/ 跃升真解（跳跃增幅）」两个技能加成的是
 * JUMP_STRENGTH 属性，而原版跳跃根本不读该属性 → <b>技能对实际跳跃零影响</b>，
 * 但属性面板照常显示数值上涨（因为面板读的是属性）→ 看起来有用、实际没用。
 *
 * <h2>修法：精确复刻 1.21.1 的公式（数值不变，只让它真的生效）</h2>
 * 原版结果 {@code orig = 0.42f * blockJumpFactor + boost}，反推
 * {@code blockJumpFactor = (orig - boost) / 0.42f}，于是：
 * <pre>
 *   新跳跃初速 = 属性值 × blockJumpFactor + boost      ← 与 1.21.1 原版公式逐项相同
 * </pre>
 * 因此跳跃初速随属性值<b>线性</b>增长（每级 +0.1 = 显示的「每级 +0.1 跳跃强度」），
 * 两版本行为完全对齐。跳跃高度 ≈ 初速² × 6.25，故高度随等级呈平方增长
 * —— 与 1.21.1 原版表现一致，未做额外改动。
 *
 * <h2>安全边界（为什么不会影响原版 / 其他模组）</h2>
 * <ul>
 *   <li><b>只作用于玩家</b>：非玩家（马等）立即早退。马的 JUMP_STRENGTH 基准值不同、
 *       跳跃另有实现，绝不能受影响。</li>
 *   <li><b>无技能加成时零改动</b>：{@code delta == 0} 早退并返回原版结果。
 *       玩家基准值已由 {@code SkillEvents.registerPlayerAttributes} 设为 0.42
 *       （= 1.21.1 原版同值；1.20.1 该属性注册名是 horse.jump_strength、默认 0.7，
 *       那是马匹用值，对玩家从无意义）。</li>
 *   <li><b>不访问 protected 的 getBlockJumpFactor()</b>：由原版返回值反推，
 *       避免再踩「reobf 后字段/方法名混淆」的坑。</li>
 *   <li><b>{@code require = 1}</b>：注入失败立即抛出，<b>不静默降级</b>。
 *       本模组的 {@code RangedAttributeMixin} 曾因静默失败（白名单恒为空、全属性被 clamp 到
 *       原版上限）潜伏三个版本才被发现，教训不再重犯。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpPowerMixin {

    /**
     * 原版跳跃初速常量（1.20.1 {@code getJumpPower} 内硬编码的 0.42f）。
     *
     * <p>⚠️ 必须写成 {@code 0.42F}（= 0.41999998688697815）而<b>不是</b> {@code 0.42D}：
     * 1.21.1 原版 JUMP_STRENGTH 的默认值就是 {@code 0.41999998688697815d}
     * （float 0.42 加宽而来，见其字节码 {@code ldc2_w}）。用同值做基准，
     * 两版本的跳跃高度才能<b>逐位一致</b>。
     */
    private static final double VANILLA_JUMP_POWER = 0.42F;

    @Inject(method = "getJumpPower", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$applyJumpStrength(CallbackInfoReturnable<Float> cir) {
        // 只改玩家：马等生物不走这里（基准值/跳跃逻辑都不同）
        if (!(((Object) this) instanceof Player player)) {
            return;
        }
        final AttributeInstance inst = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (inst == null) {
            return;
        }
        // 技能带来的增量：无加成 = 0 → 直接返回原版结果（零改动、对绝大多数实体零开销）
        //   用「当前值 - 基准值」而不是直接用当前值：即使基准值被其他模组改过，
        //   无技能时也仍是原版行为（不会出现「人人跳得更高」的灾难）。
        final double delta = inst.getValue() - inst.getBaseValue();
        if (delta == 0.0D) {
            return;
        }
        // 由原版结果反推 blockJumpFactor（原版 = 0.42f * factor + boost）
        final float boost = player.getJumpBoostPower();
        final float jumpFactor = (cir.getReturnValueF() - boost) / (float) VANILLA_JUMP_POWER;
        // 与 1.21.1 原版同式：属性值 × factor + boost
        cir.setReturnValue((float) (VANILLA_JUMP_POWER + delta) * jumpFactor + boost);
    }
}
