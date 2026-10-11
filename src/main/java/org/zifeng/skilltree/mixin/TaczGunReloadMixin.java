package org.zifeng.skilltree.mixin;

import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.skilltree.compat.TaczSkills;

/**
 * 枪械技能 · 快速换弹（★ 2026-10-11 第二版，TaCZ 兼容）。
 *
 * <h2>为什么不再改 {@code defaultTickReload}</h2>
 * 初版用 {@code @Redirect} 缩短 {@code GunReloadTime.getEmptyTime()/getTacticalTime()}，
 * 实测"换弹速度不生效"。反编译 TaCZ 后确认原因：
 * <pre>
 *   ModernKineticGunItem.tickReload(...)
 *       → gunIndex.getScript() != null ? [Lua 脚本接管] : defaultTickReload(api)
 * </pre>
 * TaCZ 默认枪里 <b>54 把有换弹配置、其中 24 把带 Lua 脚本</b>（AK-47、沙鹰等全在内），
 * 这些枪的换弹时间由脚本自己算 —— {@code defaultTickReload} <b>根本不会被调用</b>，
 * 重定向形同虚设。
 *
 * <h2>现在的做法：在两条路径的<b>唯一汇合点</b>压缩时间</h2>
 * 无论走 Lua 还是走默认实现，服务端每 tick 都必须经过
 * {@link LivingEntityReload#tickReloadState()}（它内部才去调 {@code gunItem.tickReload}）。
 * 而 TaCZ 判断"换弹是否结束"用的是<b>时间戳差值</b>：
 * <pre>
 *   // ModernKineticGunScriptAPI#getReloadTime()
 *   if (dataHolder.reloadTimestamp == -1) return 0;
 *   return System.currentTimeMillis() - dataHolder.reloadTimestamp;   // = 已耗时
 * </pre>
 * 于是只要把 {@code reloadTimestamp} 往前挪，已耗时就会"走得更快"，
 * 两条路径都会提前结束换弹。
 *
 * <p>要缩短到 {@code 1 - r} 倍，就把时间轴压缩 {@code 1 / (1 - r)} 倍：
 * 每次 tick 额外把时间戳回拨 {@code delta × r / (1 - r)}
 * （{@code delta} = 距上一 tick 的真实毫秒数，≈50）。这样换弹总时长
 * <b>恰好</b>变成 {@code base × (1 - r)} —— 数学上精确，且<b>不需要知道基础时长</b>。
 *
 * <p>⚠️ 双端一致：{@code LivingEntityReload} 是服务端权威逻辑；
 * 客户端只是收到 {@code getSynReloadState()} 后播放动画，服务端提前结束即动画提前收尾。
 *
 * <p>⚠️ TaCZ 是模组类 → 注解与字段必须 {@code remap = false}。
 */
@Mixin(value = LivingEntityReload.class, remap = false)
public abstract class TaczGunReloadMixin {

    /** TaCZ 的射击者（private final，无 getter） */
    @Shadow
    @Final
    private LivingEntity shooter;

    /** TaCZ 的换弹状态容器（内含 public long reloadTimestamp） */
    @Shadow
    @Final
    private ShooterDataHolder data;

    /** 本次换弹是否已在计时（用于跳过第一 tick 不可信的 delta） */
    @Unique
    private boolean zifeng$inReload = false;

    /** 上一 tick 的毫秒时间戳 */
    @Unique
    private long zifeng$lastTickMs = 0L;

    @Inject(remap = false, method = "tickReloadState", at = @At("HEAD"))
    private void zifeng$speedUpReload(CallbackInfoReturnable<ReloadState> cir) {
        long now = System.currentTimeMillis();
        if (this.data.reloadTimestamp == -1L) {
            this.zifeng$inReload = false; // 已结束/被打断 → 下次换弹重新计时
            return;
        }
        if (!this.zifeng$inReload) {
            // 换弹第一 tick：只记时（此刻"距上次调用"可能是上一轮换弹的间隔，不可信）
            this.zifeng$inReload = true;
            this.zifeng$lastTickMs = now;
            // 自证：任何人只要触发换弹就会打印，可证明本 injector 已匹配（require=0 会静默失效）
            TaczSkills.logActiveOnce("快速换弹 tickReloadState");
            return;
        }
        long delta = now - this.zifeng$lastTickMs;
        this.zifeng$lastTickMs = now;
        if (delta <= 0L) {
            return; // 同一毫秒内被调用两次（双端/重复 tick）→ 跳过
        }
        double reduction = TaczSkills.reloadReduction(this.shooter);
        if (reduction <= 0.0) {
            return; // 未学 / 已关闭 → 不动时间轴
        }
        // 时间轴压缩 1/(1-r) 倍 ⇒ 总时长恰好 ×(1-r)
        this.data.reloadTimestamp -= (long) (delta * (reduction / (1.0 - reduction)));
    }
}
