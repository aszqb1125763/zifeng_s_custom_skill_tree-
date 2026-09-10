package org.zifeng.skilltree.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.zifeng.skilltree.client.HealthBarHelper;

/**
 * 血条压缩（2026-09-11，1.4.0 血条卡顿修复）—— <b>1.20.1 Forge 版</b>。
 *
 * <h3>问题</h3>
 * 本模组可把最大生命推到 10 万量级，而原版血条「每 2 点生命 1 颗心」→ 5 万颗心
 * → 每帧 10 万次 blit（每颗心还伴随 enableBlend/disableBlend）→ 帧数掉到 2。
 *
 * <h3>核心原则（用户明确要求，兼容性优先）</h3>
 * <b>原版血条渲染代码一个字都不改</b>——只把「喂给原版渲染器的数值」等比压缩，
 * 让原版自己照常画（永远最多 10 颗心 = 1 行）。
 * <p>因此其他血条 mod（ColorfulHearts 等）接管渲染后<b>照常生效</b>，
 * 它们显示自己的方案，我们在旁边加真实数字即可。
 *
 * <h3>⚠️ 1.20.1 血条实际渲染路径（2026-09-11 实测确认，勿再猜）</h3>
 * Forge 用 {@code ForgeGui extends Gui} 接管 HUD，并<b>自己重写了一套血条逻辑</b>：
 * <pre>
 *   ForgeGui.render(GuiGraphics, float)
 *     └─ this.renderHealth(int, int, GuiGraphics)   ← 1.20.1 Forge 真正画血条的地方
 *          └─ this.renderHearts(...)                 ← 继承自 Gui（未重写）
 * </pre>
 * {@code Gui.renderPlayerHealth} 是 <b>private</b>，Forge 完全不走它。
 * 往那里注入 {@code @Redirect} 会「注入成功但永不执行」（表现为血条完全不压缩）。
 * <br><b>因此 1.20.1 必须注入 {@link ForgeGui#renderHealth}。</b>
 *
 * <h3>为什么只改 3 个取值点就够</h3>
 * {@code ForgeGui.renderHealth} 内的全部数值都由这 3 处派生：
 * <pre>
 *   int   i  = ceil(getHealth())                          // ← ① 当前生命
 *   float f  = getAttribute(MAX_HEALTH).getValue()        // ← ② 最大生命（心数 = f/2、行数 = (f+吸收)/2/10）
 *   int   k1 = ceil(getAbsorptionAmount())                // ← ③ 伤害吸收
 *   → 行数 / 行距 / 血条与护甲 y 坐标 / renderHearts 的 4 个入参
 * </pre>
 * 改掉这 3 处后，血条颗数、行数、位置全部自动一致。
 *
 * <h3>安全措施</h3>
 * <ul>
 *   <li>真实最大生命 ≤ 20（原版 10 颗心）时 {@link HealthBarHelper} 原样返回 → <b>零干预</b></li>
 *   <li>全部 {@code require = 0}：注入失败（与其他 mod 冲突）只静默跳过，绝不崩游戏</li>
 *   <li>三个目标调用点在 {@code renderHealth} 中各<b>只出现 1 次</b>（已用字节码核对）</li>
 * </ul>
 */
@Mixin(ForgeGui.class)
public abstract class GuiHeartsCompressMixin {

    /** ① 当前生命：等比压缩（喂给原版画心用） */
    @Redirect(
            method = "renderHealth",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getHealth()F"),
            require = 0
    )
    private float zifeng$compressHealth(Player player) {
        float real = player.getHealth();
        float realMax = player.getMaxHealth();
        if (!HealthBarHelper.shouldCompress(realMax)) {
            return real; // 未超阈值：原版行为，零干预
        }
        return HealthBarHelper.compressValue(real, realMax);
    }

    /**
     * ② 最大生命：压缩到阈内（决定心数与行数）。
     * <p>⚠️ Forge 用的是 {@code getAttribute(MAX_HEALTH).getValue()}，
     * 而非原版 Gui 的 {@code getAttributeValue(...)}——注入目标必须对应。
     * <p>该方法内 {@code AttributeInstance.getValue()} 仅此 1 处调用，故按值自判即可。
     */
    @Redirect(
            method = "renderHealth",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D"),
            require = 0
    )
    private double zifeng$compressMaxHealth(AttributeInstance instance) {
        double real = instance.getValue();
        if (!HealthBarHelper.shouldCompress((float) real)) {
            return real; // 未超阈值：原版行为，零干预
        }
        return HealthBarHelper.compressMax((float) real);
    }

    /** ③ 伤害吸收：同样压缩（基准取 max(最大生命, 吸收量)，防海量吸收盾爆炸） */
    @Redirect(
            method = "renderHealth",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"),
            require = 0
    )
    private float zifeng$compressAbsorption(Player player) {
        float real = player.getAbsorptionAmount();
        float realMax = player.getMaxHealth();
        if (!HealthBarHelper.shouldCompress(Math.max(realMax, real))) {
            return real; // 未超阈值：原版行为，零干预
        }
        return HealthBarHelper.compressAbsorption(real, realMax);
    }

    /**
     * 捕获原版护甲图标的 y 坐标（供护甲数字对齐使用）。
     * <p>{@code ForgeGui.renderArmor} 内 {@code int top = height - leftHeight}：
     * 用 {@code @Redirect} 拦截对 {@code leftHeight} 字段的<b>读取</b>，即可拿到原版
     * 当帧真实使用的值（实测值，非推算），存给渲染器用 {@code guiHeight - leftHeight} 算坐标。
     * <p>⚠️ 必须 {@code remap = false}：{@code renderArmor} 与 {@code leftHeight} 都是
     * <b>Forge 自己的成员</b>（原版 Gui 没有 {@code renderArmor}；{@code leftHeight} 在
     * ForgeGui 上），SRG 里没有对应名，运行时也<b>不混淆、保持原名</b>。
     * 不加 remap=false 时 Mixin AP 会因找不到映射直接编译报错。
     */
    @Redirect(
            method = "renderArmor",
            remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraftforge/client/gui/overlay/ForgeGui;leftHeight:I",
                    remap = false),
            require = 0
    )
    private int zifeng$captureArmorLeftHeight(ForgeGui gui) {
        int leftHeight = gui.leftHeight;
        HealthBarHelper.setArmorLeftHeight(leftHeight);
        return leftHeight;
    }
}
