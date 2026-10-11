package org.zifeng.skilltree.mixin;

import com.tacz.guns.entity.shooter.LivingEntityDrawGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.skilltree.compat.TaczSkills;

/**
 * 枪械技能 · 快速切枪（★ 2026-10-11 第三版，TaCZ 兼容）。
 *
 * <h2>⚠️ 第二版为什么整段失效</h2>
 * 第二版想偷懒 —— {@code @Redirect} 只写了 {@code at}、没写 {@code method}，
 * 指望 Mixin 去匹配目标类的全部调用点。实测<b>Mixin 不允许</b>：
 * <pre>
 *   InvalidInjectionException @Redirect annotation on zifeng$drawTime
 *       is missing 'method' or 'target' to specify targets
 * </pre>
 * 注入整块被丢弃 →「快速切枪」完全没有效果（游戏不崩，静默失效）。
 *
 * <h2>本版：只注入<b>具名实例方法</b>，不碰编译器生成的 lambda</h2>
 * TaCZ 举枪/收枪计时里，{@code GunData.getDrawTime()} / {@code getPutAwayTime()} 的调用点
 * 都落在编译器生成的私有 lambda（{@code lambda$getDrawCoolDown$0}、
 * {@code lambda$updatePutAwayTime$1}）里。用它们有两个问题：
 * <ul>
 *   <li><b>lambda 名会变</b>（换编译器 / TaCZ 升版就失配），而失配在 {@code require = 0} 下
 *       只会静默失效、不报错，排查成本极高；</li>
 *   <li>{@code lambda$updatePutAwayTime$1} 是 {@code static} 的 → 拿不到 {@code this.shooter}，
 *       根本查不了玩家技能。</li>
 * </ul>
 * 所以改为注入两个有意义的实例方法（名字稳定、都能拿到 shooter）：
 *
 * <table border="1">
 *   <caption>两段计时分别怎么压缩</caption>
 *   <tr><th>阶段</th><th>注入点</th><th>做法</th></tr>
 *   <tr>
 *     <td>举枪</td>
 *     <td>{@code getDrawCoolDown()}</td>
 *     <td>它内部算「还需等待多少毫秒」：
 *         {@code drawTime*1000 - (now - drawTimestamp) - 5}，且<b>每次调用都从
 *         drawTimestamp 重算</b>。所以把<b>返回值</b>乘 {@code (1-r)}，
 *         「归零」就发生在 {@code elapsed >= drawTime*1000*(1-r)} —— 总时长精确变为
 *         {@code base*(1-r)}（不会像累加型计时那样把缩放抵消掉）。</td>
 *   </tr>
 *   <tr>
 *     <td>收枪</td>
 *     <td>{@code updatePutAwayTime()}</td>
 *     <td>该方法是 {@code data.currentPutAwayTimeS} 的<b>唯一写入点</b>
 *         （javap 确认：偏移 78 / 89 两个分支），在返回处整体缩放一次即可覆盖两者。</td>
 *   </tr>
 * </table>
 */
@Mixin(value = LivingEntityDrawGun.class, remap = false)
public abstract class TaczDrawSpeedMixin {

    /** TaCZ 的射击者（private final，无 getter） */
    @Shadow
    @Final
    private LivingEntity shooter;

    /** TaCZ 的持枪状态容器（含 public float currentPutAwayTimeS） */
    @Shadow
    @Final
    private ShooterDataHolder data;

    /** 举枪：把「剩余冷却毫秒」按比例缩短 */
    @Inject(remap = false, method = "getDrawCoolDown", at = @At("RETURN"), cancellable = true)
    private void zifeng$shortenDrawTime(CallbackInfoReturnable<Long> cir) {
        long remaining = cir.getReturnValue();
        if (remaining <= 0L) {
            return; // TaCZ 用 0 / -1 表示「无冷却」→ 不碰
        }
        float mult = TaczSkills.drawTimeMultiplier(this.shooter);
        if (mult >= 1.0F) {
            return; // 未学 / 已关闭 → 保持原计时
        }
        TaczSkills.logActiveOnce("快速切枪 getDrawCoolDown");
        cir.setReturnValue((long) (remaining * mult));
    }

    /** 收枪：把「收枪时长」按比例缩短 */
    @Inject(remap = false, method = "updatePutAwayTime", at = @At("RETURN"))
    private void zifeng$shortenPutAwayTime(CallbackInfo ci) {
        float mult = TaczSkills.drawTimeMultiplier(this.shooter);
        if (mult >= 1.0F) {
            return; // 未学 / 已关闭 → 保持原时长
        }
        if (this.data.currentPutAwayTimeS > 0.0F) {
            this.data.currentPutAwayTimeS *= mult;
        }
    }
}
