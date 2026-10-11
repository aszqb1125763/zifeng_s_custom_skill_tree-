package org.zifeng.skilltree.mixin;

import com.tacz.guns.api.modifier.ParameterizedCache;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.zifeng.skilltree.compat.TaczSkills;

/**
 * 枪械技能 · 后坐力抑制（★ 2026-10-11 第二版，TaCZ 兼容）。
 *
 * <h2>注入点</h2>
 * TaCZ 的相机后坐力不是每帧现算的，而是在<b>开火瞬间</b>把后坐力曲线编译成两条样条函数：
 * <pre>
 *   // CameraSetupEvent#initialCameraRecoil(GunFireEvent) —— 仅客户端
 *   pitchSplineFunction = gunData.getRecoil().genPitchSplineFunction(
 *           (float) recoilPair.left().eval(aimProgressMultiplier));
 *   yawSplineFunction   = gunData.getRecoil().genYawSplineFunction(
 *           (float) recoilPair.right().eval(aimProgressMultiplier));
 * </pre>
 * 之后 {@code applyCameraRecoil} 每帧对样条取值并写到玩家视角上。
 * <p>所以 <b>只要缩放 {@code eval(...)} 的返回值</b>（它就是套在整条后坐力曲线上的倍率），
 * 两条样条就会等比例变缓 —— 后坐力整体降低，且<b>不需要碰任何静态字段</b>。
 *
 * <h2>为什么用 @Redirect 而不是改属性缓存</h2>
 * 缓存里的 {@code RECOIL} 是 {@code ParameterizedCachePair}：它只暴露「默认值 + 修饰器列表」，
 * 而修饰器列表<b>没有 getter</b>，无法在保留配件加成的前提下追加我们自己的修饰器。
 * 在应用点缩放则完全绕开了这个问题。
 *
 * <p>⚠️ {@code @Redirect} 未指定 {@code method} → 匹配目标类中<b>全部</b>该调用点
 * （正好是 left/right 两处），也避免硬编码编译器生成的 lambda 名。
 * <p>⚠️ 客户端类 → 玩家从 {@code Minecraft.getInstance().player} 取。
 */
@Mixin(value = CameraSetupEvent.class, remap = false)
public abstract class TaczRecoilMixin {

    /**
     * 缩放后坐力倍率。
     *
     * <p>⚠️ {@code method} 必须写：Mixin <b>不会</b>因为「只给了 at」就去匹配全部调用点，
     * 缺失时会直接抛 {@code InvalidInjectionException ... is missing 'method' or 'target'}，
     * 整块注入被丢弃（★ 2026-10-11 实测踩坑 —— 这让「后坐力抑制」曾完全失效）。
     * <p>好在这里的两处 {@code eval(...)} 调用（对应 pitch / yaw 两条曲线）
     * <b>都在 {@code initialCameraRecoil} 里</b>（javap 确认偏移 232 / 255），
     * 所以写死这一个方法名即可同时覆盖两条曲线，无需碰编译器生成的 lambda。
     *
     * @param cache    TaCZ 的后坐力参数缓存（左右各一条曲线）
     * @param base     原本的输入基准值（瞄准进度倍率）
     */
    @Redirect(remap = false, method = "initialCameraRecoil",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/modifier/ParameterizedCache;eval(D)D"))
    private static double zifeng$scaleRecoil(ParameterizedCache<?> cache, double base) {
        double value = cache.eval(base);
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return value;
        }
        float mult = TaczSkills.recoilMultiplier(player);
        if (mult >= 1.0F) {
            return value;
        }
        TaczSkills.logActiveOnce("后坐力抑制 initialCameraRecoil");
        return value * mult;
    }
}
